/*
 * © 2025 Murr | https://github.com/vtstv
 */
package repositories

import (
	"github.com/vtstv/nexy/internal/database"
)

type UserRepository struct {
	db *database.DB
}

func NewUserRepository(db *database.DB) *UserRepository {
	return &UserRepository{db: db}
}
