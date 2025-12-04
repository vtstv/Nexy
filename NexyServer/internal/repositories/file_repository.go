package repositories

import (
	"context"

	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/models"
)

type FileRepository struct {
	db *database.DB
}

func NewFileRepository(db *database.DB) *FileRepository {
	return &FileRepository{db: db}
}

func (r *FileRepository) Create(ctx context.Context, file *models.File) error {
	query := `
		INSERT INTO files (file_id, user_id, filename, original_filename, mime_type, file_size, storage_type, storage_path, url)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		RETURNING id, created_at`

	return r.db.QueryRowContext(ctx, query,
		file.FileID,
		file.UserID,
		file.Filename,
		file.OriginalFilename,
		file.MimeType,
		file.FileSize,
		file.StorageType,
		file.StoragePath,
		file.URL,
	).Scan(&file.ID, &file.CreatedAt)
}

func (r *FileRepository) GetByFileID(ctx context.Context, fileID string) (*models.File, error) {
	file := &models.File{}
	query := `
		SELECT id, file_id, user_id, filename, original_filename, mime_type, file_size, storage_type, storage_path, url, created_at
		FROM files
		WHERE file_id = $1`

	err := r.db.QueryRowContext(ctx, query, fileID).Scan(
		&file.ID,
		&file.FileID,
		&file.UserID,
		&file.Filename,
		&file.OriginalFilename,
		&file.MimeType,
		&file.FileSize,
		&file.StorageType,
		&file.StoragePath,
		&file.URL,
		&file.CreatedAt,
	)

	return file, err
}

func (r *FileRepository) Delete(ctx context.Context, fileID string) error {
	query := `DELETE FROM files WHERE file_id = $1`
	_, err := r.db.ExecContext(ctx, query, fileID)
	return err
}
