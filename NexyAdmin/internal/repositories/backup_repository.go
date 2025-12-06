package repositories

import (
	"compress/gzip"
	"context"
	"database/sql"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/vtstv/nexy-admin/internal/models"
)

type BackupRepository struct {
	db         *sql.DB
	backupPath string
}

func NewBackupRepository(db *sql.DB, backupPath string) *BackupRepository {
	os.MkdirAll(backupPath, 0755)
	return &BackupRepository{
		db:         db,
		backupPath: backupPath,
	}
}

func (r *BackupRepository) Create(ctx context.Context) (string, error) {
	timestamp := time.Now().Format("20060102_150405")
	filename := fmt.Sprintf("nexy_backup_%s.sql.gz", timestamp)
	fullPath := filepath.Join(r.backupPath, filename)

	outFile, err := os.Create(fullPath)
	if err != nil {
		return "", fmt.Errorf("failed to create backup file: %w", err)
	}
	defer outFile.Close()

	gzWriter := gzip.NewWriter(outFile)
	defer gzWriter.Close()

	tables := []string{
		"users", "refresh_tokens", "user_sessions", "invite_links",
		"chats", "chat_members", "chat_invite_links",
		"messages", "message_status",
		"files", "e2e_keys", "e2e_identity_keys", "e2e_signed_prekeys", "e2e_onetime_prekeys",
		"contacts",
	}

	gzWriter.Write([]byte("-- Nexy Database Backup\n"))
	gzWriter.Write([]byte(fmt.Sprintf("-- Created: %s\n\n", time.Now().Format("2006-01-02 15:04:05"))))

	for _, table := range tables {
		gzWriter.Write([]byte(fmt.Sprintf("\n-- Table: %s\n", table)))

		var exists bool
		err := r.db.QueryRowContext(ctx,
			"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = $1)",
			table).Scan(&exists)

		if err != nil || !exists {
			continue
		}

		rows, err := r.db.QueryContext(ctx, fmt.Sprintf("SELECT * FROM %s", table))
		if err != nil {
			continue
		}

		columns, err := rows.Columns()
		if err != nil {
			rows.Close()
			continue
		}

		gzWriter.Write([]byte(fmt.Sprintf("TRUNCATE TABLE %s CASCADE;\n", table)))

		for rows.Next() {
			values := make([]interface{}, len(columns))
			valuePtrs := make([]interface{}, len(columns))
			for i := range values {
				valuePtrs[i] = &values[i]
			}

			if err := rows.Scan(valuePtrs...); err != nil {
				continue
			}

			insertSQL := fmt.Sprintf("INSERT INTO %s (", table)
			for i, col := range columns {
				if i > 0 {
					insertSQL += ", "
				}
				insertSQL += col
			}
			insertSQL += ") VALUES ("

			for i, val := range values {
				if i > 0 {
					insertSQL += ", "
				}
				if val == nil {
					insertSQL += "NULL"
				} else {
					switch v := val.(type) {
					case []byte:
						insertSQL += fmt.Sprintf("'%s'", string(v))
					case string:
						escaped := v
						escaped = fmt.Sprintf("'%s'", escaped)
						insertSQL += escaped
					case time.Time:
						insertSQL += fmt.Sprintf("'%s'", v.Format("2006-01-02 15:04:05"))
					case bool:
						if v {
							insertSQL += "true"
						} else {
							insertSQL += "false"
						}
					default:
						insertSQL += fmt.Sprintf("%v", v)
					}
				}
			}
			insertSQL += ");\n"
			gzWriter.Write([]byte(insertSQL))
		}
		rows.Close()
	}

	return filename, nil
}

func (r *BackupRepository) List(ctx context.Context) ([]models.BackupInfo, error) {
	files, err := os.ReadDir(r.backupPath)
	if err != nil {
		return nil, err
	}

	var backups []models.BackupInfo
	for _, file := range files {
		if file.IsDir() {
			continue
		}

		info, err := file.Info()
		if err != nil {
			continue
		}

		backups = append(backups, models.BackupInfo{
			Filename:  file.Name(),
			Size:      info.Size(),
			CreatedAt: info.ModTime(),
		})
	}

	return backups, nil
}

func (r *BackupRepository) Restore(ctx context.Context, filename string) error {
	fullPath := filepath.Join(r.backupPath, filename)

	file, err := os.Open(fullPath)
	if err != nil {
		return fmt.Errorf("failed to open backup file: %w", err)
	}
	defer file.Close()

	gzReader, err := gzip.NewReader(file)
	if err != nil {
		return fmt.Errorf("failed to decompress backup: %w", err)
	}
	defer gzReader.Close()

	content := make([]byte, 0)
	buffer := make([]byte, 4096)
	for {
		n, err := gzReader.Read(buffer)
		if n > 0 {
			content = append(content, buffer[:n]...)
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return fmt.Errorf("failed to read backup: %w", err)
		}
	}

	sqlContent := string(content)

	_, err = r.db.ExecContext(ctx, sqlContent)
	if err != nil {
		return fmt.Errorf("failed to restore backup: %w", err)
	}

	return nil
}

func (r *BackupRepository) Delete(ctx context.Context, filename string) error {
	filepath := filepath.Join(r.backupPath, filename)
	return os.Remove(filepath)
}

func (r *BackupRepository) GetPath(filename string) string {
	return filepath.Join(r.backupPath, filename)
}

func (r *BackupRepository) ReadFile(filename string) (io.ReadCloser, error) {
	filepath := filepath.Join(r.backupPath, filename)
	return os.Open(filepath)
}
