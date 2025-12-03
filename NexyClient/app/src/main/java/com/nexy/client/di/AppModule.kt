package com.nexy.client.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nexy.client.ServerConfig
import com.nexy.client.data.api.AuthInterceptor
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.api.TokenAuthenticator
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.NexyDatabase
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.websocket.NexyWebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
            level = HttpLoggingInterceptor.Level.BODY
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
        ).build()
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
    fun provideNexyWebSocketClient(gson: Gson): NexyWebSocketClient {
        return NexyWebSocketClient(WS_URL, gson)
    }
}
