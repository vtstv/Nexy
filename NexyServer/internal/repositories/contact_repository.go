package repositories

import (
	"database/sql"
	"fmt"

	"github.com/vtstv/nexy/internal/models"
)

type ContactRepository struct {
	db *sql.DB
}

func NewContactRepository(db *sql.DB) *ContactRepository {
	return &ContactRepository{db: db}
}

func (r *ContactRepository) AddContact(userID, contactUserID int) error {
	query := `
		INSERT INTO contacts (user_id, contact_user_id, status)
		VALUES ($1, $2, 'accepted')
		ON CONFLICT (user_id, contact_user_id) DO NOTHING
	`
	_, err := r.db.Exec(query, userID, contactUserID)
	return err
}

func (r *ContactRepository) GetContacts(userID int) ([]models.ContactWithUser, error) {
	query := `
		SELECT 
			c.id, c.user_id, c.contact_user_id, c.status, c.created_at, c.updated_at,
			u.id, u.username, u.email, u.display_name, u.avatar_url, u.bio, u.created_at, u.updated_at
		FROM contacts c
		JOIN users u ON c.contact_user_id = u.id
		WHERE c.user_id = $1 AND c.status = 'accepted'
		ORDER BY u.display_name
	`

	rows, err := r.db.Query(query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var contacts []models.ContactWithUser
	for rows.Next() {
		var c models.ContactWithUser
		err := rows.Scan(
			&c.ID, &c.UserID, &c.ContactUserID, &c.Status, &c.CreatedAt, &c.UpdatedAt,
			&c.ContactUser.ID, &c.ContactUser.Username, &c.ContactUser.Email,
			&c.ContactUser.DisplayName, &c.ContactUser.AvatarURL, &c.ContactUser.Bio,
			&c.ContactUser.CreatedAt, &c.ContactUser.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		contacts = append(contacts, c)
	}

	return contacts, nil
}

func (r *ContactRepository) UpdateContactStatus(userID, contactUserID int, status string) error {
	query := `
		UPDATE contacts 
		SET status = $1, updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $2 AND contact_user_id = $3
	`
	result, err := r.db.Exec(query, status, userID, contactUserID)
	if err != nil {
		return err
	}

	rows, err := result.RowsAffected()
	if err != nil {
		return err
	}

	if rows == 0 {
		return fmt.Errorf("contact not found")
	}

	return nil
}

func (r *ContactRepository) DeleteContact(userID, contactUserID int) error {
	query := `DELETE FROM contacts WHERE user_id = $1 AND contact_user_id = $2`
	result, err := r.db.Exec(query, userID, contactUserID)
	if err != nil {
		return err
	}

	rows, err := result.RowsAffected()
	if err != nil {
		return err
	}

	if rows == 0 {
		return fmt.Errorf("contact not found")
	}

	return nil
}

func (r *ContactRepository) CheckContactExists(userID, contactUserID int) (bool, error) {
	var exists bool
	query := `SELECT EXISTS(SELECT 1 FROM contacts WHERE user_id = $1 AND contact_user_id = $2)`
	err := r.db.QueryRow(query, userID, contactUserID).Scan(&exists)
	return exists, err
}

func (r *ContactRepository) GetContactStatus(userID, contactUserID int) (string, error) {
	var status string
	query := `SELECT status FROM contacts WHERE user_id = $1 AND contact_user_id = $2`
	err := r.db.QueryRow(query, userID, contactUserID).Scan(&status)
	if err == sql.ErrNoRows {
		return "", nil
	}
	return status, err
}
