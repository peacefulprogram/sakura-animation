package com.jing.sakura.di

import com.jing.sakura.repo.WebPageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.*
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
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

}