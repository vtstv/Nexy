package repositories

import (
	"context"
	"log"

	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/models"
)

type FolderRepository struct {
	db *database.DB
}

func NewFolderRepository(db *database.DB) *FolderRepository {
	return &FolderRepository{db: db}
}

func (r *FolderRepository) Create(ctx context.Context, folder *models.ChatFolder) error {
	query := `
		INSERT INTO chat_folders (user_id, name, icon, color, position, include_contacts, include_non_contacts, include_groups, include_channels, include_bots)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		RETURNING id, created_at, updated_at`

	return r.db.QueryRowContext(ctx, query,
		folder.UserID,
		folder.Name,
		folder.Icon,
		folder.Color,
		folder.Position,
		folder.IncludeContacts,
		folder.IncludeNonContacts,
		folder.IncludeGroups,
		folder.IncludeChannels,
		folder.IncludeBots,
	).Scan(&folder.ID, &folder.CreatedAt, &folder.UpdatedAt)
}

func (r *FolderRepository) GetByUserID(ctx context.Context, userID int) ([]models.ChatFolder, error) {
	query := `
		SELECT id, user_id, name, icon, color, position, include_contacts, include_non_contacts, include_groups, include_channels, include_bots, created_at, updated_at
		FROM chat_folders
		WHERE user_id = $1
		ORDER BY position ASC`

	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var folders []models.ChatFolder
	for rows.Next() {
		var f models.ChatFolder
		err := rows.Scan(
			&f.ID, &f.UserID, &f.Name, &f.Icon, &f.Color, &f.Position,
			&f.IncludeContacts, &f.IncludeNonContacts, &f.IncludeGroups, &f.IncludeChannels, &f.IncludeBots,
			&f.CreatedAt, &f.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		folders = append(folders, f)
	}
	return folders, nil
}

func (r *FolderRepository) GetByID(ctx context.Context, folderID int) (*models.ChatFolder, error) {
	query := `
		SELECT id, user_id, name, icon, color, position, include_contacts, include_non_contacts, include_groups, include_channels, include_bots, created_at, updated_at
		FROM chat_folders
		WHERE id = $1`

	var f models.ChatFolder
	err := r.db.QueryRowContext(ctx, query, folderID).Scan(
		&f.ID, &f.UserID, &f.Name, &f.Icon, &f.Color, &f.Position,
		&f.IncludeContacts, &f.IncludeNonContacts, &f.IncludeGroups, &f.IncludeChannels, &f.IncludeBots,
		&f.CreatedAt, &f.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &f, nil
}

func (r *FolderRepository) Update(ctx context.Context, folder *models.ChatFolder) error {
	query := `
		UPDATE chat_folders 
		SET name = $1, icon = $2, color = $3, position = $4, 
		    include_contacts = $5, include_non_contacts = $6, include_groups = $7, include_channels = $8, include_bots = $9,
		    updated_at = CURRENT_TIMESTAMP
		WHERE id = $10 AND user_id = $11`

	_, err := r.db.ExecContext(ctx, query,
		folder.Name, folder.Icon, folder.Color, folder.Position,
		folder.IncludeContacts, folder.IncludeNonContacts, folder.IncludeGroups, folder.IncludeChannels, folder.IncludeBots,
		folder.ID, folder.UserID,
	)
	return err
}

func (r *FolderRepository) Delete(ctx context.Context, folderID, userID int) error {
	query := `DELETE FROM chat_folders WHERE id = $1 AND user_id = $2`
	_, err := r.db.ExecContext(ctx, query, folderID, userID)
	return err
}

func (r *FolderRepository) UpdatePositions(ctx context.Context, userID int, positions map[int]int) error {
	for folderID, position := range positions {
		query := `UPDATE chat_folders SET position = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2 AND user_id = $3`
		_, err := r.db.ExecContext(ctx, query, position, folderID, userID)
		if err != nil {
			return err
		}
	}
	return nil
}

// Included chats (uses chat_folder_items table)
func (r *FolderRepository) GetIncludedChats(ctx context.Context, folderID int) ([]int, error) {
	query := `SELECT chat_id FROM chat_folder_items WHERE folder_id = $1`
	rows, err := r.db.QueryContext(ctx, query, folderID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var chatIDs []int
	for rows.Next() {
		var id int
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		chatIDs = append(chatIDs, id)
	}
	return chatIDs, nil
}

func (r *FolderRepository) SetIncludedChats(ctx context.Context, folderID int, chatIDs []int) error {
	log.Printf("SetIncludedChats: folderID=%d, chatIDs=%v", folderID, chatIDs)

	// Delete existing
	result, err := r.db.ExecContext(ctx, `DELETE FROM chat_folder_items WHERE folder_id = $1`, folderID)
	if err != nil {
		log.Printf("SetIncludedChats DELETE error: %v", err)
		return err
	}
	deleted, _ := result.RowsAffected()
	log.Printf("SetIncludedChats: deleted %d rows", deleted)

	// Insert new
	for _, chatID := range chatIDs {
		result, err := r.db.ExecContext(ctx,
			`INSERT INTO chat_folder_items (folder_id, chat_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
			folderID, chatID)
		if err != nil {
			log.Printf("SetIncludedChats INSERT error: %v", err)
			return err
		}
		inserted, _ := result.RowsAffected()
		log.Printf("SetIncludedChats: inserted chat %d, rows affected: %d", chatID, inserted)
	}
	return nil
}

func (r *FolderRepository) RemoveChatFromFolder(ctx context.Context, folderID, chatID int) error {
	_, err := r.db.ExecContext(ctx,
		`DELETE FROM chat_folder_items WHERE folder_id = $1 AND chat_id = $2`,
		folderID, chatID)
	return err
}

// Excluded chats - not used with simple schema, return empty
func (r *FolderRepository) GetExcludedChats(ctx context.Context, folderID int) ([]int, error) {
	return []int{}, nil
}

func (r *FolderRepository) SetExcludedChats(ctx context.Context, folderID int, chatIDs []int) error {
	// Not implemented with simple schema
	return nil
}

// Get folder with included/excluded chats
func (r *FolderRepository) GetFolderWithChats(ctx context.Context, folderID int) (*models.ChatFolderWithChats, error) {
	folder, err := r.GetByID(ctx, folderID)
	if err != nil {
		return nil, err
	}

	included, err := r.GetIncludedChats(ctx, folderID)
	if err != nil {
		return nil, err
	}

	excluded, err := r.GetExcludedChats(ctx, folderID)
	if err != nil {
		return nil, err
	}

	return &models.ChatFolderWithChats{
		ChatFolder:    *folder,
		IncludedChats: included,
		ExcludedChats: excluded,
	}, nil
}

// Get all folders with their chats for a user
func (r *FolderRepository) GetAllFoldersWithChats(ctx context.Context, userID int) ([]models.ChatFolderWithChats, error) {
	folders, err := r.GetByUserID(ctx, userID)
	if err != nil {
		return nil, err
	}

	var result []models.ChatFolderWithChats
	for _, f := range folders {
		included, _ := r.GetIncludedChats(ctx, f.ID)
		excluded, _ := r.GetExcludedChats(ctx, f.ID)
		result = append(result, models.ChatFolderWithChats{
			ChatFolder:    f,
			IncludedChats: included,
			ExcludedChats: excluded,
		})
	}
	return result, nil
}
