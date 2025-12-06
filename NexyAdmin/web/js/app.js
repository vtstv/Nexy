// Copyright (c) 2025 Nexy Project. All rights reserved.
// GitHub: https://github.com/vtstv/Nexy

const API_BASE = '/api';

const api = axios.create({
    baseURL: API_BASE,
    headers: {
        'Content-Type': 'application/json'
    }
});

api.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

const DashboardView = {
    template: `
        <div>
            <div class="view-header">
                <h1><i class="fas fa-home"></i> Dashboard</h1>
            </div>

            <div v-if="loading" class="loading">
                <i class="fas fa-spinner fa-spin"></i>
            </div>

            <div v-else>
                <div class="stats-grid">
                    <div class="stat-card">
                        <div class="stat-icon" style="background: linear-gradient(135deg, #667eea, #764ba2)">
                            <i class="fas fa-users"></i>
                        </div>
                        <div class="stat-info">
                            <h3>Total Users</h3>
                            <p>{{ stats.total_users || 0 }}</p>
                        </div>
                    </div>

                    <div class="stat-card">
                        <div class="stat-icon" style="background: linear-gradient(135deg, #48bb78, #38a169)">
                            <i class="fas fa-user-check"></i>
                        </div>
                        <div class="stat-info">
                            <h3>Online Users</h3>
                            <p>{{ stats.online_users || 0 }}</p>
                        </div>
                    </div>

                    <div class="stat-card">
                        <div class="stat-icon" style="background: linear-gradient(135deg, #4299e1, #3182ce)">
                            <i class="fas fa-comments"></i>
                        </div>
                        <div class="stat-info">
                            <h3>Total Chats</h3>
                            <p>{{ stats.total_chats || 0 }}</p>
                        </div>
                    </div>

                    <div class="stat-card">
                        <div class="stat-icon" style="background: linear-gradient(135deg, #ed8936, #dd6b20)">
                            <i class="fas fa-envelope"></i>
                        </div>
                        <div class="stat-info">
                            <h3>Total Messages</h3>
                            <p>{{ stats.total_messages || 0 }}</p>
                        </div>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <h2>User Growth (Last 30 Days)</h2>
                    </div>
                    <div class="chart-container">
                        <canvas ref="userChart"></canvas>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <h2>Message Activity (Last 30 Days)</h2>
                    </div>
                    <div class="chart-container">
                        <canvas ref="messageChart"></canvas>
                    </div>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            loading: true,
            stats: {}
        };
    },
    async mounted() {
        await this.loadStats();
    },
    methods: {
        async loadStats() {
            try {
                const { data } = await api.get('/stats/overview');
                this.stats = data;
                this.$nextTick(() => {
                    this.renderCharts();
                });
            } catch (error) {
                console.error('Failed to load stats:', error);
            } finally {
                this.loading = false;
            }
        },
        renderCharts() {
            if (this.stats.user_growth && this.$refs.userChart) {
                new Chart(this.$refs.userChart, {
                    type: 'line',
                    data: {
                        labels: this.stats.user_growth.map(d => d.date),
                        datasets: [{
                            label: 'New Users',
                            data: this.stats.user_growth.map(d => d.value),
                            borderColor: '#667eea',
                            backgroundColor: 'rgba(102, 126, 234, 0.1)',
                            tension: 0.4
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            legend: { display: false }
                        }
                    }
                });
            }

            if (this.stats.message_activity && this.$refs.messageChart) {
                new Chart(this.$refs.messageChart, {
                    type: 'bar',
                    data: {
                        labels: this.stats.message_activity.map(d => d.date),
                        datasets: [{
                            label: 'Messages',
                            data: this.stats.message_activity.map(d => d.value),
                            backgroundColor: 'rgba(72, 187, 120, 0.8)'
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            legend: { display: false }
                        }
                    }
                });
            }
        }
    }
};

const UsersView = {
    template: `
        <div>
            <div class="view-header">
                <h1><i class="fas fa-users"></i> Users</h1>
            </div>

            <div class="card">
                <div class="search-bar">
                    <input v-model="search" @input="loadUsers" placeholder="Search users..." />
                </div>

                <div v-if="loading" class="loading">
                    <i class="fas fa-spinner fa-spin"></i>
                </div>

                <table v-else-if="users.length">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Username</th>
                            <th>Email</th>
                            <th>Display Name</th>
                            <th>Status</th>
                            <th>Created</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="user in users" :key="user.id" :class="{'banned-user': user.is_banned}">
                            <td>{{ user.id }}</td>
                            <td>
                                {{ user.username }}
                                <span v-if="user.is_banned" class="badge badge-danger" style="margin-left: 0.5rem;">BANNED</span>
                            </td>
                            <td>{{ user.email }}</td>
                            <td>{{ user.display_name }}</td>
                            <td>
                                <span v-if="user.show_online_status" class="badge badge-success">Online</span>
                                <span v-else class="badge badge-danger">Offline</span>
                            </td>
                            <td>{{ formatDate(user.created_at) }}</td>
                            <td>
                                <button @click="viewUser(user.id)" class="btn btn-primary" style="padding: 0.25rem 0.75rem; font-size: 0.75rem;">
                                    <i class="fas fa-eye"></i>
                                </button>
                                <button v-if="!user.is_banned" @click="banUser(user.id)" class="btn btn-warning" style="padding: 0.25rem 0.75rem; font-size: 0.75rem; margin-left: 0.5rem;" title="Ban User">
                                    <i class="fas fa-ban"></i>
                                </button>
                                <button v-else @click="unbanUser(user.id)" class="btn btn-success" style="padding: 0.25rem 0.75rem; font-size: 0.75rem; margin-left: 0.5rem;" title="Unban User">
                                    <i class="fas fa-check"></i>
                                </button>
                                <button @click="deleteUser(user.id)" class="btn btn-danger" style="padding: 0.25rem 0.75rem; font-size: 0.75rem; margin-left: 0.5rem;">
                                    <i class="fas fa-trash"></i>
                                </button>
                            </td>
                        </tr>
                    </tbody>
                </table>

                <div v-else class="empty-state">
                    <i class="fas fa-users"></i>
                    <p>No users found</p>
                </div>

                <div v-if="totalPages > 1" class="pagination">
                    <button @click="changePage(page - 1)" :disabled="page === 1">Previous</button>
                    <button v-for="p in totalPages" :key="p" @click="changePage(p)" :class="{active: p === page}">
                        {{ p }}
                    </button>
                    <button @click="changePage(page + 1)" :disabled="page === totalPages">Next</button>
                </div>
            </div>

            <!-- User Details Modal -->
            <div v-if="selectedUser" class="modal-overlay" @click="selectedUser = null">
                <div class="modal-content" @click.stop>
                    <div class="modal-header">
                        <h2><i class="fas fa-user"></i> User Details</h2>
                        <button @click="selectedUser = null" class="close-btn">&times;</button>
                    </div>
                    <div class="modal-body">
                        <div class="user-detail-row">
                            <strong>ID:</strong> <span>{{ selectedUser.id }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Username:</strong> <span>{{ selectedUser.username }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Email:</strong> <span>{{ selectedUser.email }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Display Name:</strong> <span>{{ selectedUser.display_name }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Bio:</strong> <span>{{ selectedUser.bio || 'No bio' }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Avatar:</strong> <span>{{ selectedUser.avatar_url || 'No avatar' }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Online Status:</strong> 
                            <span v-if="selectedUser.show_online_status" class="badge badge-success">Online</span>
                            <span v-else class="badge badge-danger">Offline</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Last Seen:</strong> <span>{{ formatDateTime(selectedUser.last_seen) }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Read Receipts:</strong> 
                            <span v-if="selectedUser.read_receipts_enabled" class="badge badge-success">Enabled</span>
                            <span v-else class="badge badge-secondary">Disabled</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Typing Indicators:</strong> 
                            <span v-if="selectedUser.typing_indicators_enabled" class="badge badge-success">Enabled</span>
                            <span v-else class="badge badge-secondary">Disabled</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Voice Messages:</strong> 
                            <span v-if="selectedUser.voice_messages_enabled" class="badge badge-success">Enabled</span>
                            <span v-else class="badge badge-secondary">Disabled</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Account Status:</strong> 
                            <span v-if="selectedUser.is_banned" class="badge badge-danger">BANNED</span>
                            <span v-else class="badge badge-success">Active</span>
                        </div>
                        <div v-if="selectedUser.is_banned && selectedUser.banned_at" class="user-detail-row">
                            <strong>Banned At:</strong> <span>{{ formatDateTime(selectedUser.banned_at) }}</span>
                        </div>
                        <div v-if="selectedUser.is_banned && selectedUser.banned_reason" class="user-detail-row">
                            <strong>Ban Reason:</strong> <span style="color: #e53e3e;">{{ selectedUser.banned_reason }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Created:</strong> <span>{{ formatDateTime(selectedUser.created_at) }}</span>
                        </div>
                        <div class="user-detail-row">
                            <strong>Updated:</strong> <span>{{ formatDateTime(selectedUser.updated_at) }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            loading: true,
            users: [],
            search: '',
            page: 1,
            pageSize: 20,
            totalPages: 1,
            selectedUser: null
        };
    },
    async mounted() {
        await this.loadUsers();
    },
    methods: {
        async loadUsers() {
            this.loading = true;
            try {
                const { data } = await api.get('/users', {
                    params: {
                        page: this.page,
                        page_size: this.pageSize,
                        search: this.search
                    }
                });
                this.users = data.data || [];
                this.totalPages = data.total_pages || 1;
            } catch (error) {
                console.error('Failed to load users:', error);
            } finally {
                this.loading = false;
            }
        },
        changePage(newPage) {
            if (newPage >= 1 && newPage <= this.totalPages) {
                this.page = newPage;
                this.loadUsers();
            }
        },
        async viewUser(id) {
            try {
                const { data } = await api.get(`/users/${id}`);
                this.selectedUser = data;
            } catch (error) {
                alert('Failed to load user details');
            }
        },
        async banUser(id) {
            const reason = prompt('Enter ban reason:');
            if (reason) {
                try {
                    await api.post(`/users/${id}/ban`, { reason });
                    alert('User banned successfully');
                    await this.loadUsers();
                } catch (error) {
                    alert('Failed to ban user: ' + (error.response?.data || error.message));
                }
            }
        },
        async unbanUser(id) {
            if (confirm('Are you sure you want to unban this user?')) {
                try {
                    await api.post(`/users/${id}/unban`);
                    alert('User unbanned successfully');
                    await this.loadUsers();
                } catch (error) {
                    alert('Failed to unban user: ' + (error.response?.data || error.message));
                }
            }
        },
        async deleteUser(id) {
            if (confirm('Are you sure you want to delete this user?')) {
                try {
                    await api.delete(`/users/${id}`);
                    await this.loadUsers();
                } catch (error) {
                    alert('Failed to delete user');
                }
            }
        },
        formatDate(date) {
            return new Date(date).toLocaleDateString();
        },
        formatDateTime(date) {
            return new Date(date).toLocaleString();
        }
    }
};

const ChatsView = {
    template: `
        <div>
            <div class="view-header">
                <h1><i class="fas fa-comments"></i> Chats</h1>
            </div>

            <div class="card">
                <div class="search-bar">
                    <input v-model="search" @input="loadChats" placeholder="Search chats..." />
                </div>

                <div v-if="loading" class="loading">
                    <i class="fas fa-spinner fa-spin"></i>
                </div>

                <table v-else-if="chats.length">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Type</th>
                            <th>Name</th>
                            <th>Members</th>
                            <th>Created</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="chat in chats" :key="chat.id">
                            <td>{{ chat.id }}</td>
                            <td>
                                <span class="badge badge-info">{{ chat.type }}</span>
                            </td>
                            <td>{{ getChatDisplayName(chat) }}</td>
                            <td>{{ chat.member_count }}</td>
                            <td>{{ formatDate(chat.created_at) }}</td>
                            <td>
                                <button @click="viewChat(chat.id)" class="btn btn-primary" style="padding: 0.25rem 0.75rem; font-size: 0.75rem;">
                                    <i class="fas fa-eye"></i>
                                </button>
                                <button @click="viewChatMessages(chat.id, chat)" class="btn btn-info" style="padding: 0.25rem 0.75rem; font-size: 0.75rem; margin-left: 0.5rem;">
                                    <i class="fas fa-envelope"></i>
                                </button>
                                <button @click="deleteChat(chat.id)" class="btn btn-danger" style="padding: 0.25rem 0.75rem; font-size: 0.75rem; margin-left: 0.5rem;">
                                    <i class="fas fa-trash"></i>
                                </button>
                            </td>
                        </tr>
                    </tbody>
                </table>

                <div v-else class="empty-state">
                    <i class="fas fa-comments"></i>
                    <p>No chats found</p>
                </div>

                <div v-if="totalPages > 1" class="pagination">
                    <button @click="changePage(page - 1)" :disabled="page === 1">Previous</button>
                    <button v-for="p in totalPages" :key="p" @click="changePage(p)" :class="{active: p === page}">
                        {{ p }}
                    </button>
                    <button @click="changePage(page + 1)" :disabled="page === totalPages">Next</button>
                </div>
            </div>

            <!-- Chat Details Modal -->
            <div v-if="selectedChat" class="modal-overlay" @click="selectedChat = null">
                <div class="modal-content modal-content-wide" @click.stop>
                    <div class="modal-header">
                        <h2><i class="fas fa-comments"></i> Chat Details</h2>
                        <button @click="selectedChat = null" class="close-btn">&times;</button>
                    </div>
                    <div class="modal-body">
                        <div class="chat-detail-section">
                            <h3><i class="fas fa-info-circle"></i> Basic Information</h3>
                            <div class="user-detail-row">
                                <strong>Chat ID:</strong> <span>{{ selectedChat.id }}</span>
                            </div>
                            <div class="user-detail-row">
                                <strong>Type:</strong> 
                                <span class="badge badge-info">{{ selectedChat.type }}</span>
                            </div>
                            <div v-if="selectedChat.group_type" class="user-detail-row">
                                <strong>Group Type:</strong> 
                                <span class="badge badge-secondary">{{ selectedChat.group_type }}</span>
                            </div>
                            <div class="user-detail-row">
                                <strong>Name:</strong> <span>{{ selectedChat.name || 'N/A' }}</span>
                            </div>
                            <div v-if="selectedChat.username" class="user-detail-row">
                                <strong>Username:</strong> <span>@{{ selectedChat.username }}</span>
                            </div>
                            <div v-if="selectedChat.description" class="user-detail-row">
                                <strong>Description:</strong> <span>{{ selectedChat.description }}</span>
                            </div>
                            <div v-if="selectedChat.avatar_url" class="user-detail-row">
                                <strong>Avatar:</strong> <span>{{ selectedChat.avatar_url }}</span>
                            </div>
                            <div class="user-detail-row">
                                <strong>Member Count:</strong> <span>{{ selectedChat.member_count }}</span>
                            </div>
                            <div class="user-detail-row">
                                <strong>Created:</strong> <span>{{ formatDateTime(selectedChat.created_at) }}</span>
                            </div>
                            <div class="user-detail-row">
                                <strong>Updated:</strong> <span>{{ formatDateTime(selectedChat.updated_at) }}</span>
                            </div>
                        </div>

                        <div v-if="chatMembers.length" class="chat-detail-section">
                            <h3><i class="fas fa-users"></i> Members ({{ chatMembers.length }})</h3>
                            <div class="members-list">
                                <div v-for="member in chatMembers" :key="member.id" class="member-card">
                                    <div class="member-avatar">
                                        <i v-if="!member.avatar_url" class="fas fa-user"></i>
                                        <img v-else :src="member.avatar_url" :alt="member.display_name">
                                    </div>
                                    <div class="member-info">
                                        <div class="member-name">
                                            {{ member.display_name }}
                                            <span class="badge" :class="getRoleBadgeClass(member.role)">{{ member.role }}</span>
                                        </div>
                                        <div class="member-username">@{{ member.username }}</div>
                                        <div class="member-joined">Joined: {{ formatDate(member.joined_at) }}</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Chat Messages Modal -->
            <div v-if="showMessagesModal" class="modal-overlay" @click="showMessagesModal = false">
                <div class="modal-content modal-content-wide modal-messages" @click.stop>
                    <div class="modal-header">
                        <h2><i class="fas fa-envelope"></i> Messages: {{ currentChatName }}</h2>
                        <button @click="showMessagesModal = false" class="close-btn">&times;</button>
                    </div>
                    
                    <div class="messages-filters">
                        <input v-model="messageFilters.search" @input="loadChatMessages" placeholder="Search messages..." class="filter-input">
                        <input v-model="messageFilters.startDate" @change="loadChatMessages" type="date" class="filter-input">
                        <input v-model="messageFilters.endDate" @change="loadChatMessages" type="date" class="filter-input">
                        <select v-model="messageFilters.messageType" @change="loadChatMessages" class="filter-input">
                            <option value="">All Types</option>
                            <option value="text">Text</option>
                            <option value="media">Media</option>
                            <option value="file">File</option>
                            <option value="voice_message">Voice</option>
                            <option value="system">System</option>
                        </select>
                        <button @click="clearMessageFilters" class="btn btn-secondary" style="padding: 0.5rem 1rem;">
                            <i class="fas fa-times"></i> Clear
                        </button>
                    </div>

                    <div class="modal-body">
                        <div v-if="loadingMessages" class="loading">
                            <i class="fas fa-spinner fa-spin"></i>
                        </div>

                        <div v-else-if="chatMessages.length" class="messages-list">
                            <div v-for="message in chatMessages" :key="message.id" class="message-item">
                                <div class="message-header">
                                    <span class="message-sender">{{ message.sender_name }}</span>
                                    <span class="badge" :class="getMessageTypeBadge(message.message_type)">{{ message.message_type }}</span>
                                    <span class="message-date">{{ formatDateTime(message.created_at) }}</span>
                                </div>
                                <div class="message-content">
                                    <div v-if="message.message_type === 'text'" class="message-text">
                                        {{ message.content }}
                                    </div>
                                    <div v-else-if="message.message_type === 'media' || message.message_type === 'file'" class="message-media">
                                        <i class="fas fa-file"></i> {{ message.media_url || message.content }}
                                    </div>
                                    <div v-else-if="message.message_type === 'voice_message'" class="message-voice">
                                        <i class="fas fa-microphone"></i> Voice Message
                                    </div>
                                    <div v-else class="message-system">
                                        {{ message.content }}
                                    </div>
                                    <span v-if="message.is_edited" class="message-edited">(edited)</span>
                                </div>
                            </div>
                        </div>

                        <div v-else class="empty-state">
                            <i class="fas fa-envelope"></i>
                            <p>No messages found</p>
                        </div>

                        <div v-if="messagesTotalPages > 1" class="pagination">
                            <button @click="changeMessagesPage(messagesPage - 1)" :disabled="messagesPage === 1">Previous</button>
                            <button v-for="p in Math.min(messagesTotalPages, 10)" :key="p" 
                                @click="changeMessagesPage(p)" 
                                :class="{active: p === messagesPage}">
                                {{ p }}
                            </button>
                            <button @click="changeMessagesPage(messagesPage + 1)" :disabled="messagesPage === messagesTotalPages">Next</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            loading: true,
            chats: [],
            search: '',
            page: 1,
            pageSize: 20,
            totalPages: 1,
            selectedChat: null,
            chatMembers: [],
            showMessagesModal: false,
            currentChatId: null,
            currentChatName: '',
            chatMessages: [],
            loadingMessages: false,
            messagesPage: 1,
            messagesPageSize: 50,
            messagesTotalPages: 1,
            messageFilters: {
                search: '',
                startDate: '',
                endDate: '',
                messageType: ''
            }
        };
    },
    async mounted() {
        await this.loadChats();
    },
    methods: {
        async loadChats() {
            this.loading = true;
            try {
                const { data } = await api.get('/chats', {
                    params: {
                        page: this.page,
                        page_size: this.pageSize,
                        search: this.search
                    }
                });
                this.chats = data.data || [];
                this.totalPages = data.total_pages || 1;
            } catch (error) {
                console.error('Failed to load chats:', error);
            } finally {
                this.loading = false;
            }
        },
        changePage(newPage) {
            if (newPage >= 1 && newPage <= this.totalPages) {
                this.page = newPage;
                this.loadChats();
            }
        },
        async viewChat(id) {
            try {
                const { data } = await api.get(`/chats/${id}`);
                this.selectedChat = data;
                
                // Load chat members
                const membersResponse = await api.get(`/chats/${id}/members`);
                this.chatMembers = membersResponse.data || [];
            } catch (error) {
                console.error('Failed to load chat details:', error);
                alert('Failed to load chat details');
            }
        },
        async deleteChat(id) {
            if (confirm('Are you sure you want to delete this chat?')) {
                try {
                    await api.delete(`/chats/${id}`);
                    await this.loadChats();
                } catch (error) {
                    alert('Failed to delete chat');
                }
            }
        },
        async viewChatMessages(chatId, chat) {
            this.currentChatId = chatId;
            this.currentChatName = this.getChatDisplayName(chat);
            this.showMessagesModal = true;
            this.messagesPage = 1;
            this.messageFilters = {
                search: '',
                startDate: '',
                endDate: '',
                messageType: ''
            };
            await this.loadChatMessages();
        },
        async loadChatMessages() {
            this.loadingMessages = true;
            try {
                const params = {
                    page: this.messagesPage,
                    page_size: this.messagesPageSize,
                    search: this.messageFilters.search,
                    start_date: this.messageFilters.startDate,
                    end_date: this.messageFilters.endDate,
                    message_type: this.messageFilters.messageType
                };
                
                const { data } = await api.get(`/chats/${this.currentChatId}/messages`, { params });
                this.chatMessages = data.data || [];
                this.messagesTotalPages = data.total_pages || 1;
            } catch (error) {
                console.error('Failed to load chat messages:', error);
                alert('Failed to load chat messages');
            } finally {
                this.loadingMessages = false;
            }
        },
        changeMessagesPage(newPage) {
            if (newPage >= 1 && newPage <= this.messagesTotalPages) {
                this.messagesPage = newPage;
                this.loadChatMessages();
            }
        },
        clearMessageFilters() {
            this.messageFilters = {
                search: '',
                startDate: '',
                endDate: '',
                messageType: ''
            };
            this.messagesPage = 1;
            this.loadChatMessages();
        },
        getMessageTypeBadge(type) {
            switch(type) {
                case 'text': return 'badge-info';
                case 'media': return 'badge-success';
                case 'file': return 'badge-warning';
                case 'voice_message': return 'badge-primary';
                case 'system': return 'badge-secondary';
                default: return 'badge-secondary';
            }
        },
        getChatDisplayName(chat) {
            if (chat.name) {
                return chat.name;
            }
            if (chat.member_names) {
                return chat.member_names;
            }
            return 'Private Chat';
        },
        getRoleBadgeClass(role) {
            switch(role) {
                case 'owner': return 'badge-danger';
                case 'admin': return 'badge-warning';
                default: return 'badge-secondary';
            }
        },
        formatDate(date) {
            return new Date(date).toLocaleDateString();
        },
        formatDateTime(date) {
            return new Date(date).toLocaleString();
        }
    }
};

const MessagesView = {
    template: `
        <div>
            <div class="view-header">
                <h1><i class="fas fa-envelope"></i> Messages</h1>
            </div>

            <div class="card">
                <div class="search-bar">
                    <input v-model="search" @input="loadMessages" placeholder="Search messages..." />
                </div>

                <div v-if="loading" class="loading">
                    <i class="fas fa-spinner fa-spin"></i>
                </div>

                <table v-else-if="messages.length">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Sender</th>
                            <th>Chat</th>
                            <th>Type</th>
                            <th>Content</th>
                            <th>Date</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="msg in messages" :key="msg.id">
                            <td>{{ msg.id }}</td>
                            <td>{{ msg.sender_name }}</td>
                            <td>{{ msg.chat_name }}</td>
                            <td>{{ msg.message_type }}</td>
                            <td>{{ truncate(msg.content, 50) }}</td>
                            <td>{{ formatDate(msg.created_at) }}</td>
                            <td>
                                <button @click="deleteMessage(msg.id)" class="btn btn-danger" style="padding: 0.25rem 0.75rem; font-size: 0.75rem;">
                                    <i class="fas fa-trash"></i>
                                </button>
                            </td>
                        </tr>
                    </tbody>
                </table>

                <div v-else class="empty-state">
                    <i class="fas fa-envelope"></i>
                    <p>No messages found</p>
                </div>

                <div v-if="totalPages > 1" class="pagination">
                    <button @click="changePage(page - 1)" :disabled="page === 1">Previous</button>
                    <button v-for="p in totalPages" :key="p" @click="changePage(p)" :class="{active: p === page}">
                        {{ p }}
                    </button>
                    <button @click="changePage(page + 1)" :disabled="page === totalPages">Next</button>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            loading: true,
            messages: [],
            search: '',
            page: 1,
            pageSize: 50,
            totalPages: 1
        };
    },
    async mounted() {
        await this.loadMessages();
    },
    methods: {
        async loadMessages() {
            this.loading = true;
            try {
                const { data } = await api.get('/messages', {
                    params: {
                        page: this.page,
                        page_size: this.pageSize,
                        search: this.search
                    }
                });
                this.messages = data.data || [];
                this.totalPages = data.total_pages || 1;
            } catch (error) {
                console.error('Failed to load messages:', error);
            } finally {
                this.loading = false;
            }
        },
        changePage(newPage) {
            if (newPage >= 1 && newPage <= this.totalPages) {
                this.page = newPage;
                this.loadMessages();
            }
        },
        async deleteMessage(id) {
            if (confirm('Are you sure you want to delete this message?')) {
                try {
                    await api.delete(`/messages/${id}`);
                    await this.loadMessages();
                } catch (error) {
                    alert('Failed to delete message');
                }
            }
        },
        truncate(str, len) {
            if (!str) return '';
            return str.length > len ? str.substring(0, len) + '...' : str;
        },
        formatDate(date) {
            return new Date(date).toLocaleString();
        }
    }
};

const BackupView = {
    template: `
        <div>
            <div class="view-header">
                <h1><i class="fas fa-database"></i> Backup & Restore</h1>
                <button @click="createBackup" class="btn btn-success" :disabled="loading">
                    <i class="fas fa-plus"></i> Create Backup
                </button>
            </div>

            <div class="card">
                <div class="card-header">
                    <h2>Available Backups</h2>
                </div>

                <div v-if="loading" class="loading">
                    <i class="fas fa-spinner fa-spin"></i>
                </div>

                <div v-else-if="backups.length" class="backup-list">
                    <div v-for="backup in backups" :key="backup.filename" class="backup-item">
                        <div class="backup-info">
                            <strong>{{ backup.filename }}</strong>
                            <small>{{ formatSize(backup.size) }} - {{ formatDate(backup.created_at) }}</small>
                        </div>
                        <div class="backup-actions">
                            <button @click="downloadBackup(backup.filename)" class="btn btn-primary" style="padding: 0.5rem 1rem;">
                                <i class="fas fa-download"></i> Download
                            </button>
                            <button @click="restoreBackup(backup.filename)" class="btn btn-success" style="padding: 0.5rem 1rem;">
                                <i class="fas fa-undo"></i> Restore
                            </button>
                            <button @click="deleteBackup(backup.filename)" class="btn btn-danger" style="padding: 0.5rem 1rem;">
                                <i class="fas fa-trash"></i>
                            </button>
                        </div>
                    </div>
                </div>

                <div v-else class="empty-state">
                    <i class="fas fa-database"></i>
                    <p>No backups available</p>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            loading: true,
            backups: []
        };
    },
    async mounted() {
        await this.loadBackups();
    },
    methods: {
        async loadBackups() {
            this.loading = true;
            try {
                const { data } = await api.get('/backup/list');
                this.backups = data || [];
            } catch (error) {
                console.error('Failed to load backups:', error);
            } finally {
                this.loading = false;
            }
        },
        async createBackup() {
            this.loading = true;
            try {
                await api.post('/backup/create');
                alert('Backup created successfully');
                await this.loadBackups();
            } catch (error) {
                alert('Failed to create backup');
            } finally {
                this.loading = false;
            }
        },
        downloadBackup(filename) {
            window.open(`/api/backup/download/${filename}`, '_blank');
        },
        async restoreBackup(filename) {
            if (confirm('Are you sure you want to restore this backup? This will overwrite current data.')) {
                try {
                    await api.post('/backup/restore', { filename });
                    alert('Backup restored successfully');
                } catch (error) {
                    alert('Failed to restore backup');
                }
            }
        },
        async deleteBackup(filename) {
            if (confirm('Are you sure you want to delete this backup?')) {
                try {
                    await api.delete(`/backup/delete/${filename}`);
                    await this.loadBackups();
                } catch (error) {
                    alert('Failed to delete backup');
                }
            }
        },
        formatSize(bytes) {
            const mb = bytes / 1024 / 1024;
            return mb.toFixed(2) + ' MB';
        },
        formatDate(date) {
            return new Date(date).toLocaleString();
        }
    }
};

const DiagnosticsView = {
    template: `
        <div>
            <div class="view-header">
                <h1><i class="fas fa-stethoscope"></i> Diagnostics</h1>
                <button @click="loadDiagnostics" class="btn btn-primary">
                    <i class="fas fa-sync"></i> Refresh
                </button>
            </div>

            <div v-if="loading" class="loading">
                <i class="fas fa-spinner fa-spin"></i>
            </div>

            <div v-else class="diagnostic-grid">
                <div class="diagnostic-card">
                    <h3><i class="fas fa-heartbeat"></i> System Health</h3>
                    <div class="diagnostic-item">
                        <span>Status</span>
                        <span :class="health.status === 'healthy' ? 'badge badge-success' : 'badge badge-danger'">
                            {{ health.status }}
                        </span>
                    </div>
                </div>

                <div class="diagnostic-card">
                    <h3><i class="fas fa-database"></i> Database</h3>
                    <div class="diagnostic-item">
                        <span>Connected</span>
                        <span :class="database.connected ? 'badge badge-success' : 'badge badge-danger'">
                            {{ database.connected ? 'Yes' : 'No' }}
                        </span>
                    </div>
                    <div class="diagnostic-item">
                        <span>Open Connections</span>
                        <strong>{{ database.open_connections }}</strong>
                    </div>
                    <div class="diagnostic-item" v-if="database.table_sizes">
                        <span>Users</span>
                        <strong>{{ database.table_sizes.users || 0 }}</strong>
                    </div>
                    <div class="diagnostic-item" v-if="database.table_sizes">
                        <span>Chats</span>
                        <strong>{{ database.table_sizes.chats || 0 }}</strong>
                    </div>
                    <div class="diagnostic-item" v-if="database.table_sizes">
                        <span>Messages</span>
                        <strong>{{ database.table_sizes.messages || 0 }}</strong>
                    </div>
                </div>

                <div class="diagnostic-card">
                    <h3><i class="fas fa-server"></i> Redis</h3>
                    <div class="diagnostic-item">
                        <span>Connected</span>
                        <span :class="redis.connected ? 'badge badge-success' : 'badge badge-danger'">
                            {{ redis.connected ? 'Yes' : 'No' }}
                        </span>
                    </div>
                    <div class="diagnostic-item">
                        <span>Key Count</span>
                        <strong>{{ redis.key_count || 0 }}</strong>
                    </div>
                    <div class="diagnostic-item">
                        <span>Clients</span>
                        <strong>{{ redis.connected_clients || 0 }}</strong>
                    </div>
                </div>

                <div class="diagnostic-card">
                    <h3><i class="fas fa-microchip"></i> System</h3>
                    <div class="diagnostic-item">
                        <span>Uptime</span>
                        <strong>{{ system.uptime }}</strong>
                    </div>
                    <div class="diagnostic-item">
                        <span>Go Version</span>
                        <strong>{{ system.go_version }}</strong>
                    </div>
                    <div class="diagnostic-item">
                        <span>Goroutines</span>
                        <strong>{{ system.num_goroutine }}</strong>
                    </div>
                    <div class="diagnostic-item">
                        <span>Memory (MB)</span>
                        <strong>{{ system.mem_alloc_mb }} / {{ system.mem_total_mb }}</strong>
                    </div>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            loading: true,
            health: {},
            database: {},
            redis: {},
            system: {}
        };
    },
    async mounted() {
        await this.loadDiagnostics();
    },
    methods: {
        async loadDiagnostics() {
            this.loading = true;
            try {
                const [healthRes, dbRes, redisRes, systemRes] = await Promise.all([
                    api.get('/diagnostics/health'),
                    api.get('/diagnostics/database'),
                    api.get('/diagnostics/redis'),
                    api.get('/diagnostics/system')
                ]);

                this.health = healthRes.data;
                this.database = dbRes.data;
                this.redis = redisRes.data;
                this.system = systemRes.data;
            } catch (error) {
                console.error('Failed to load diagnostics:', error);
            } finally {
                this.loading = false;
            }
        }
    }
};

const { createApp } = Vue;

createApp({
    data() {
        return {
            isAuthenticated: false,
            loading: false,
            error: '',
            currentView: 'dashboard',
            loginForm: {
                username: '',
                password: ''
            }
        };
    },
    computed: {
        currentViewComponent() {
            const views = {
                dashboard: DashboardView,
                users: UsersView,
                chats: ChatsView,
                messages: MessagesView,
                backup: BackupView,
                diagnostics: DiagnosticsView
            };
            return views[this.currentView] || DashboardView;
        }
    },
    mounted() {
        const token = localStorage.getItem('token');
        if (token) {
            this.isAuthenticated = true;
        }
    },
    methods: {
        async login() {
            this.loading = true;
            this.error = '';

            try {
                const { data } = await api.post('/auth/login', this.loginForm);
                localStorage.setItem('token', data.token);
                this.isAuthenticated = true;
            } catch (error) {
                this.error = 'Invalid credentials';
            } finally {
                this.loading = false;
            }
        },
        logout() {
            localStorage.removeItem('token');
            this.isAuthenticated = false;
            this.currentView = 'dashboard';
        }
    }
}).mount('#app');
