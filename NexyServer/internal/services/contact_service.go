/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"fmt"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type ContactService struct {
	contactRepo *repositories.ContactRepository
	userRepo    *repositories.UserRepository
}

func NewContactService(contactRepo *repositories.ContactRepository, userRepo *repositories.UserRepository) *ContactService {
	return &ContactService{
		contactRepo: contactRepo,
		userRepo:    userRepo,
	}
}

func (s *ContactService) AddContact(userID, contactUserID int) error {
	if userID == contactUserID {
		return fmt.Errorf("cannot add yourself as a contact")
	}

	// Check if contact user exists
	_, err := s.userRepo.GetByID(context.Background(), contactUserID)
	if err != nil {
		return fmt.Errorf("user not found")
	}

	// Check if already exists
	exists, err := s.contactRepo.CheckContactExists(userID, contactUserID)
	if err != nil {
		return err
	}

	if exists {
		return fmt.Errorf("contact already exists")
	}

	// Add bidirectional contact
	err = s.contactRepo.AddContact(userID, contactUserID)
	if err != nil {
		return err
	}

	err = s.contactRepo.AddContact(contactUserID, userID)
	if err != nil {
		return err
	}

	return nil
}

func (s *ContactService) GetContacts(userID int) ([]models.ContactWithUser, error) {
	return s.contactRepo.GetContacts(userID)
}

func (s *ContactService) UpdateContactStatus(userID, contactUserID int, status string) error {
	if status != "accepted" && status != "blocked" {
		return fmt.Errorf("invalid status: must be 'accepted' or 'blocked'")
	}

	exists, err := s.contactRepo.CheckContactExists(userID, contactUserID)
	if err != nil {
		return err
	}

	if !exists {
		return fmt.Errorf("contact not found")
	}

	return s.contactRepo.UpdateContactStatus(userID, contactUserID, status)
}

func (s *ContactService) DeleteContact(userID, contactUserID int) error {
	// Delete bidirectional contact
	err := s.contactRepo.DeleteContact(userID, contactUserID)
	if err != nil {
		return err
	}

	// Try to delete reverse, but don't fail if it doesn't exist
	_ = s.contactRepo.DeleteContact(contactUserID, userID)

	return nil
}

func (s *ContactService) CheckContactExists(userID, contactUserID int) (bool, error) {
	return s.contactRepo.CheckContactExists(userID, contactUserID)
}

func (s *ContactService) GetContactStatus(userID, contactUserID int) (string, error) {
	return s.contactRepo.GetContactStatus(userID, contactUserID)
}
