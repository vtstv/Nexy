package services

import (
	"context"

	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/repositories"
)

type BackupService struct {
	repo *repositories.BackupRepository
}

func NewBackupService(repo *repositories.BackupRepository) *BackupService {
	return &BackupService{repo: repo}
}

func (s *BackupService) CreateBackup(ctx context.Context) (string, error) {
	return s.repo.Create(ctx)
}

func (s *BackupService) ListBackups(ctx context.Context) ([]models.BackupInfo, error) {
	return s.repo.List(ctx)
}

func (s *BackupService) RestoreBackup(ctx context.Context, filename string) error {
	return s.repo.Restore(ctx, filename)
}

func (s *BackupService) DeleteBackup(ctx context.Context, filename string) error {
	return s.repo.Delete(ctx, filename)
}

func (s *BackupService) GetBackupPath(filename string) string {
	return s.repo.GetPath(filename)
}
