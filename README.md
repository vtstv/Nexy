# Nexy - Messaging Platform (WIP)

A modern, messaging application with end-to-end encryption (E2EE), built with Go backend and Android Kotlin client.

## 🔐 Features

### Security
- **End-to-End Encryption (E2EE)** - Messages encrypted on device, decrypted only by recipient
- **Signal Protocol Implementation** - Industry-standard encryption protocol WIP
- **Biometric Authentication** - Fingerprint and face unlock support
- **PIN Lock** - Additional security layer for app access
- **Self-Destructing Messages** - Automatic message deletion after set time

### Messaging
- **Real-time Communication** - WebSocket-based instant messaging
- **Group Chats** - Create and manage group conversations with admin controls
- **File Sharing** - Send images, videos, voice messages, and documents
- **Voice Messages** - Record and send audio messages
- **Message Reactions** - React to messages with emojis
- **Read Receipts** - See when messages are delivered and read
- **Message Search** - Find messages across all conversations

### Social Features
- **Contact Management** - Add contacts via username or QR code
- **User Profiles** - Customizable profiles with avatars and bio
- **Online Status** - See who's currently online
- **Typing Indicators** - Real-time typing status
- **QR Code Invites** - Quick contact addition via QR scanning

### Customization
- **Theme System** - Dark/Light/Auto modes with custom accent colors
- **Custom Color Picker** - Choose your preferred accent color
- **Material Design 3** - Modern, beautiful UI with dynamic theming

## 🏗️ Architecture

### Backend (Go)
- **Framework**: Native Go HTTP server
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Real-time**: WebSocket connections
- **Authentication**: JWT tokens with refresh mechanism
- **File Storage**: Local filesystem with organized structure
- **Containerization**: Docker & Docker Compose

### Android Client (Kotlin)
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: MVVM with Clean Architecture principles
- **Dependency Injection**: Hilt
- **Local Database**: Room
- **Networking**: Retrofit + OkHttp
- **WebSocket**: OkHttp WebSocket
- **Image Loading**: Coil
- **Encryption**: Custom E2E implementation

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose (for server)
- PostgreSQL 15
- Redis 7
- Android Studio (for client)
- Android SDK (API 26+)
- JDK 17+

### Server Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/vtstv/nexy.git
   cd nexy
   ```

2. **Configure environment**
   ```bash
   cd NexyServer
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Start with Docker Compose**
   ```bash
   docker-compose up -d
   ```

4. **Server will be available at**
   - HTTP: `http://localhost:8080`
   - WebSocket: `ws://localhost:8080/ws`

### Android Client Setup

1. **Open in Android Studio**
   ```bash
   cd NexyClient
   # Open this folder in Android Studio
   ```

2. **Configure local properties**
   ```bash
   # Create local.properties with your Android SDK path
   sdk.dir=/path/to/Android/Sdk
   ```

3. **Configure API endpoint**
   - Edit `app/src/main/java/com/nexy/client/data/remote/ApiConfig.kt`
   - Set `BASE_URL` to your server address

4. **Build and Run**
   - Build: `./gradlew assembleDebug`
   - Or run directly from Android Studio

## 📁 Project Structure

```
nexy/
├── NexyServer/              # Go backend server
│   ├── cmd/
│   │   └── server/         # Application entry point
│   ├── internal/
│   │   ├── controllers/    # HTTP handlers
│   │   ├── services/       # Business logic
│   │   ├── models/         # Data models
│   │   ├── repositories/   # Database layer
│   │   ├── middleware/     # Auth, CORS, logging
│   │   ├── ws/            # WebSocket management
│   │   └── config/        # Configuration
│   ├── migrations/         # Database migrations
│   └── docker-compose.yml  # Docker setup
│
└── NexyClient/             # Android Kotlin client
    └── app/
        └── src/main/java/com/nexy/client/
            ├── ui/         # Compose UI screens
            ├── data/       # Repositories, API, database
            ├── domain/     # Use cases, models
            └── utils/      # Helpers, encryption, etc.
```

## 🔧 Configuration

### Server Environment Variables
See `NexyServer/.env.example` for all available options:
- Database connection
- JWT secrets
- Redis configuration
- CORS settings
- File upload limits

### Client Configuration
- Server URL in `ApiConfig.kt`
- App version in `build.gradle.kts`
- Signing configuration (for release builds)

## 📝 API Documentation

Key endpoints:
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `GET /api/users/me` - Get current user
- `POST /api/chats` - Create chat
- `POST /api/messages` - Send message
- `GET /api/messages/:chatId` - Get messages
- `WS /ws` - WebSocket connection


## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👤 Author

**Murr**
- GitHub: [@vtstv](https://github.com/vtstv)

© 2025 Murr | Made with ❤️
