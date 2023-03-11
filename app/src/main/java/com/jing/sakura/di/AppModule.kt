package com.jing.sakura.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.jing.sakura.BuildConfig
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.SakuraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.*
import java.time.Duration
import java.util.concurrent.Executors
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10L))
            .readTimeout(Duration.ofSeconds(10L))
            .addInterceptor(Interceptor { chain ->
                chain.request()
                    .newBuilder()
                    .header(
                        "user-agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36"
                    )
                    .build()
                    .let {
                        chain.proceed(it)
                    }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideWebDataRepo(okHttpClient: OkHttpClient) = WebPageRepository(okHttpClient)

    @Provides
    @Singleton
    fun provideRoomDatabase(@ApplicationContext context: Context): SakuraDatabase {

        val builder = Room.databaseBuilder(context, SakuraDatabase::class.java, "sk_db")
        if (BuildConfig.DEBUG) {
            builder.setQueryCallback({ sqlQuery, bindArgs ->
                Log.d("RoomQuery", "sql: $sqlQuery, args: $bindArgs")
            }, Executors.newSingleThreadExecutor())
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideVideoHistoryDao(database: SakuraDatabase) =
        database.getVideoHistoryDao()

}