FROM golang:1.24-alpine AS builder

WORKDIR /app

COPY go.mod go.sum* ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -o nexy-admin ./cmd/admin

FROM alpine:latest

RUN apk --no-cache add ca-certificates postgresql-client

WORKDIR /root/

COPY --from=builder /app/nexy-admin .
COPY --from=builder /app/web ./web

RUN mkdir -p /backups

EXPOSE 3000

CMD ["./nexy-admin"]
