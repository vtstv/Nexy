package repositories

import (
	"context"
	"database/sql"

	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/models"
)

type E2ERepository struct {
	db *database.DB
}

func NewE2ERepository(db *database.DB) *E2ERepository {
	return &E2ERepository{db: db}
}

// Identity Keys
func (r *E2ERepository) SaveIdentityKey(ctx context.Context, userID int, publicKey string) error {
	query := `
		INSERT INTO identity_keys (user_id, public_key)
		VALUES ($1, $2)
		ON CONFLICT (user_id) 
		DO UPDATE SET public_key = $2, updated_at = NOW()
	`
	_, err := r.db.ExecContext(ctx, query, userID, publicKey)
	return err
}

func (r *E2ERepository) GetIdentityKey(ctx context.Context, userID int) (*models.IdentityKey, error) {
	var key models.IdentityKey
	query := `SELECT id, user_id, public_key, created_at, updated_at FROM identity_keys WHERE user_id = $1`
	err := r.db.QueryRowContext(ctx, query, userID).Scan(
		&key.ID, &key.UserID, &key.PublicKey, &key.CreatedAt, &key.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return &key, err
}

// Signed PreKeys
func (r *E2ERepository) SaveSignedPreKey(ctx context.Context, userID, keyID int, publicKey, signature string) error {
	query := `
		INSERT INTO signed_pre_keys (user_id, key_id, public_key, signature)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (user_id, key_id) 
		DO UPDATE SET public_key = $3, signature = $4, created_at = NOW()
	`
	_, err := r.db.ExecContext(ctx, query, userID, keyID, publicKey, signature)
	return err
}

func (r *E2ERepository) GetSignedPreKey(ctx context.Context, userID int) (*models.SignedPreKey, error) {
	var key models.SignedPreKey
	query := `
		SELECT id, user_id, key_id, public_key, signature, created_at 
		FROM signed_pre_keys 
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT 1
	`
	err := r.db.QueryRowContext(ctx, query, userID).Scan(
		&key.ID, &key.UserID, &key.KeyID, &key.PublicKey, &key.Signature, &key.CreatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return &key, err
}

// One-time PreKeys
func (r *E2ERepository) SavePreKeys(ctx context.Context, userID int, keys []models.PreKey) error {
	tx, err := r.db.BeginTx(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	stmt, err := tx.PrepareContext(ctx, `
		INSERT INTO pre_keys (user_id, key_id, public_key) 
		VALUES ($1, $2, $3)
	`)
	if err != nil {
		return err
	}
	defer stmt.Close()

	for _, key := range keys {
		_, err = stmt.ExecContext(ctx, userID, key.KeyID, key.PublicKey)
		if err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (r *E2ERepository) GetAndMarkPreKey(ctx context.Context, userID int) (*models.PreKey, error) {
	tx, err := r.db.BeginTx(ctx)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	var key models.PreKey
	query := `
		SELECT id, user_id, key_id, public_key, used, created_at 
		FROM pre_keys 
		WHERE user_id = $1 AND used = false
		ORDER BY created_at ASC
		LIMIT 1
		FOR UPDATE
	`
	err = tx.QueryRowContext(ctx, query, userID).Scan(
		&key.ID, &key.UserID, &key.KeyID, &key.PublicKey, &key.Used, &key.CreatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	// Mark as used
	_, err = tx.ExecContext(ctx, `UPDATE pre_keys SET used = true WHERE id = $1`, key.ID)
	if err != nil {
		return nil, err
	}

	if err = tx.Commit(); err != nil {
		return nil, err
	}

	return &key, nil
}

func (r *E2ERepository) CountUnusedPreKeys(ctx context.Context, userID int) (int, error) {
	var count int
	query := `SELECT COUNT(*) FROM pre_keys WHERE user_id = $1 AND used = false`
	err := r.db.QueryRowContext(ctx, query, userID).Scan(&count)
	return count, err
}

// Get complete key bundle for establishing session
func (r *E2ERepository) GetKeyBundle(ctx context.Context, userID, deviceID int) (*models.KeyBundle, error) {
	identityKey, err := r.GetIdentityKey(ctx, userID)
	if err != nil {
		return nil, err
	}
	if identityKey == nil {
		return nil, sql.ErrNoRows
	}

	signedPreKey, err := r.GetSignedPreKey(ctx, userID)
	if err != nil {
		return nil, err
	}
	if signedPreKey == nil {
		return nil, sql.ErrNoRows
	}

	bundle := &models.KeyBundle{
		UserID:      userID,
		IdentityKey: identityKey.PublicKey,
		SignedPreKey: &models.KeyBundleSignedPreKey{
			KeyID:     signedPreKey.KeyID,
			PublicKey: signedPreKey.PublicKey,
			Signature: signedPreKey.Signature,
		},
		DeviceID: deviceID,
	}

	// Try to get one-time prekey (optional)
	preKey, err := r.GetAndMarkPreKey(ctx, userID)
	if err == nil && preKey != nil {
		bundle.PreKey = &models.KeyBundlePreKey{
			KeyID:     preKey.KeyID,
			PublicKey: preKey.PublicKey,
		}
	}

	return bundle, nil
}
