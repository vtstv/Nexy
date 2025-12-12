package com.nexy.client.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nexy.client.BuildConfig
import com.nexy.client.ServerConfig
import com.nexy.client.data.api.AuthInterceptor
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.api.TokenAuthenticator
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.NexyDatabase
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.FolderDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.PendingMessageDao
import com.nexy.client.data.local.dao.SearchHistoryDao
import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.websocket.NexyWebSocketClient
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    private val BASE_URL = ServerConfig.BASE_URL
    private val WS_URL = ServerConfig.WS_URL
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }
    
    @Provides
    @Singleton
    fun provideAuthTokenManager(@ApplicationContext context: Context): AuthTokenManager {
        return AuthTokenManager(context)
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
            .connectTimeout(ServerConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ServerConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ServerConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: AuthTokenManager): AuthInterceptor {
        return AuthInterceptor(tokenManager)
    }
    
    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenManager: AuthTokenManager,
        apiService: dagger.Lazy<NexyApiService>
    ): TokenAuthenticator {
        return TokenAuthenticator(tokenManager, apiService)
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideNexyApiService(retrofit: Retrofit): NexyApiService {
        return retrofit.create(NexyApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideNexyDatabase(@ApplicationContext context: Context): NexyDatabase {
        return Room.databaseBuilder(
            context,
            NexyDatabase::class.java,
            "nexy_database"
        )
        .addMigrations(
            NexyDatabase.MIGRATION_4_5,
            NexyDatabase.MIGRATION_5_6,
            NexyDatabase.MIGRATION_6_7,
            NexyDatabase.MIGRATION_7_8,
            NexyDatabase.MIGRATION_8_9,
            NexyDatabase.MIGRATION_9_10,
            NexyDatabase.MIGRATION_10_11,
            NexyDatabase.MIGRATION_11_12,
            NexyDatabase.MIGRATION_12_13
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    @Singleton
    fun provideUserDao(database: NexyDatabase): UserDao {
        return database.userDao()
    }
    
    @Provides
    @Singleton
    fun provideMessageDao(database: NexyDatabase): MessageDao {
        return database.messageDao()
    }
    
    @Provides
    @Singleton
    fun provideChatDao(database: NexyDatabase): ChatDao {
        return database.chatDao()
    }
    
    @Provides
    @Singleton
    fun providePendingMessageDao(database: NexyDatabase): PendingMessageDao {
        return database.pendingMessageDao()
    }
    
    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: NexyDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    @Singleton
    fun provideFolderDao(database: NexyDatabase): FolderDao {
        return database.folderDao()
    }
    
    @Provides
    @Singleton
    fun provideNexyWebSocketClient(
        gson: Gson,
        tokenManager: AuthTokenManager,
        apiService: Lazy<NexyApiService>
    ): NexyWebSocketClient {
        return NexyWebSocketClient(WS_URL, gson, tokenManager, apiService)
    }
    
    @Provides
    @Singleton
    fun provideE2EApiClient(okHttpClient: OkHttpClient, gson: Gson): com.nexy.client.e2e.E2EApiClient {
        return com.nexy.client.e2e.E2EApiClient(BASE_URL, okHttpClient, gson)
    }

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
