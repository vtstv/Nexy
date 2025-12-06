package repositories

import (
	"context"
	"database/sql"
	"time"

	"github.com/vtstv/nexy-admin/internal/models"
)

type StatsRepository struct {
	db *sql.DB
}

func NewStatsRepository(db *sql.DB) *StatsRepository {
	return &StatsRepository{db: db}
}

func (r *StatsRepository) GetOverview(ctx context.Context) (*models.Stats, error) {
	stats := &models.Stats{}

	err := r.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM users").Scan(&stats.TotalUsers)
	if err != nil {
		return nil, err
	}

	err = r.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM users WHERE last_seen > NOW() - INTERVAL '5 minutes'",
	).Scan(&stats.ActiveUsers)
	if err != nil {
		return nil, err
	}

	err = r.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM chats").Scan(&stats.TotalChats)
	if err != nil {
		return nil, err
	}

	err = r.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM messages WHERE is_deleted = false",
	).Scan(&stats.TotalMessages)
	if err != nil {
		return nil, err
	}

	err = r.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM messages WHERE created_at::date = CURRENT_DATE",
	).Scan(&stats.MessagesToday)
	if err != nil {
		return nil, err
	}

	err = r.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM users WHERE created_at::date = CURRENT_DATE",
	).Scan(&stats.NewUsersToday)
	if err != nil {
		return nil, err
	}

	err = r.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM users WHERE show_online_status = true AND last_seen > NOW() - INTERVAL '5 minutes'",
	).Scan(&stats.OnlineUsers)
	if err != nil {
		return nil, err
	}

	return stats, nil
}

func (r *StatsRepository) GetUserGrowth(ctx context.Context, days int) ([]models.TimeSeriesData, error) {
	query := `
		SELECT DATE(created_at) as date, COUNT(*) as count
		FROM users
		WHERE created_at >= NOW() - INTERVAL '1 day' * $1
		GROUP BY DATE(created_at)
		ORDER BY date ASC`

	rows, err := r.db.QueryContext(ctx, query, days)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var data []models.TimeSeriesData
	for rows.Next() {
		var item models.TimeSeriesData
		var date time.Time
		err := rows.Scan(&date, &item.Value)
		if err != nil {
			return nil, err
		}
		item.Date = date.Format("2006-01-02")
		data = append(data, item)
	}

	return data, nil
}

func (r *StatsRepository) GetMessageActivity(ctx context.Context, days int) ([]models.TimeSeriesData, error) {
	query := `
		SELECT DATE(created_at) as date, COUNT(*) as count
		FROM messages
		WHERE created_at >= NOW() - INTERVAL '1 day' * $1 AND is_deleted = false
		GROUP BY DATE(created_at)
		ORDER BY date ASC`

	rows, err := r.db.QueryContext(ctx, query, days)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var data []models.TimeSeriesData
	for rows.Next() {
		var item models.TimeSeriesData
		var date time.Time
		err := rows.Scan(&date, &item.Value)
		if err != nil {
			return nil, err
		}
		item.Date = date.Format("2006-01-02")
		data = append(data, item)
	}

	return data, nil
}

func (r *StatsRepository) GetChatStats(ctx context.Context) (map[string]int, error) {
	stats := make(map[string]int)

	rows, err := r.db.QueryContext(ctx, "SELECT type, COUNT(*) FROM chats GROUP BY type")
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var chatType string
		var count int
		err := rows.Scan(&chatType, &count)
		if err != nil {
			return nil, err
		}
		stats[chatType] = count
	}

	return stats, nil
}
