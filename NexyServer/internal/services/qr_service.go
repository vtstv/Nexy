/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"bytes"
	"encoding/base64"
	"fmt"

	qrcode "github.com/skip2/go-qrcode"
)

type QRService struct{}

func NewQRService() *QRService {
	return &QRService{}
}

func (s *QRService) GenerateQRCode(data string) (string, error) {
	qr, err := qrcode.New(data, qrcode.Medium)
	if err != nil {
		return "", fmt.Errorf("failed to generate QR code: %w", err)
	}

	png, err := qr.PNG(256)
	if err != nil {
		return "", fmt.Errorf("failed to encode QR code: %w", err)
	}

	var buf bytes.Buffer
	buf.Write(png)

	encoded := base64.StdEncoding.EncodeToString(buf.Bytes())
	return "data:image/png;base64," + encoded, nil
}
