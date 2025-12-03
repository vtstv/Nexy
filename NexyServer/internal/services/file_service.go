/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"fmt"
	"io"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/vtstv/nexy/internal/config"
	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type FileService struct {
	fileRepo *repositories.FileRepository
	config   *config.UploadConfig
}

func NewFileService(fileRepo *repositories.FileRepository, cfg *config.UploadConfig) *FileService {
	return &FileService{
		fileRepo: fileRepo,
		config:   cfg,
	}
}

func (s *FileService) UploadFile(ctx context.Context, userID int, fileHeader *multipart.FileHeader, fileType string) (*models.File, error) {
	if fileHeader.Size > s.config.MaxSize {
		return nil, fmt.Errorf("file size exceeds maximum allowed size")
	}

	mimeType := fileHeader.Header.Get("Content-Type")
	if !s.isAllowedMimeType(mimeType) {
		return nil, fmt.Errorf("file type not allowed")
	}

	// Determine subfolder based on mime type
	subfolder := "files"
	if strings.HasPrefix(mimeType, "image/") {
		subfolder = "images"
	} else if strings.HasPrefix(mimeType, "video/") {
		subfolder = "videos"
	} else if strings.HasPrefix(mimeType, "audio/") {
		subfolder = "audio"
	}

	// Create date-based directory structure: type/YYYY/MM/DD
	now := time.Now()
	uploadDir := filepath.Join(
		s.config.Path,
		subfolder,
		fmt.Sprintf("%04d", now.Year()),
		fmt.Sprintf("%02d", now.Month()),
		fmt.Sprintf("%02d", now.Day()),
	)

	fileID := uuid.New().String()
	ext := filepath.Ext(fileHeader.Filename)
	filename := fileID + ext
	storagePath := filepath.Join(uploadDir, filename)

	if err := os.MkdirAll(uploadDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create upload directory: %w", err)
	}

	src, err := fileHeader.Open()
	if err != nil {
		return nil, fmt.Errorf("failed to open uploaded file: %w", err)
	}
	defer src.Close()

	dst, err := os.Create(storagePath)
	if err != nil {
		return nil, fmt.Errorf("failed to create file: %w", err)
	}
	defer dst.Close()

	if _, err := io.Copy(dst, src); err != nil {
		return nil, fmt.Errorf("failed to save file: %w", err)
	}

	file := &models.File{
		FileID:           fileID,
		UserID:           userID,
		Filename:         filename,
		OriginalFilename: fileHeader.Filename,
		MimeType:         mimeType,
		FileSize:         fileHeader.Size,
		StorageType:      "local",
		StoragePath:      storagePath,
		URL:              "/files/" + fileID,
	}

	if err := s.fileRepo.Create(ctx, file); err != nil {
		os.Remove(storagePath)
		return nil, fmt.Errorf("failed to save file metadata: %w", err)
	}

	return file, nil
}

func (s *FileService) GetFileByID(ctx context.Context, fileID string) (*models.File, error) {
	return s.fileRepo.GetByFileID(ctx, fileID)
}

func (s *FileService) isAllowedMimeType(mimeType string) bool {
	for _, allowed := range s.config.AllowedMimeTypes {
		if strings.TrimSpace(allowed) == mimeType {
			return true
		}
	}
	return false
}
