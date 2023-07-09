package com.jing.sakura

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.jing.sakura.detail.DetailPageViewModel
import com.jing.sakura.history.HistoryViewModel
import com.jing.sakura.home.HomeViewModel
import com.jing.sakura.http.WebServerContext
import com.jing.sakura.player.VideoPlayerViewModel
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.SakuraDatabase
import com.jing.sakura.search.SearchResultViewModel
import com.jing.sakura.search.SearchViewModel
import com.jing.sakura.timeline.TimelineViewModel
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SakuraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = this
        startKoin {
            androidContext(this@SakuraApplication)
            androidLogger()
            modules(httpModule(), roomModule(), viewModelModule())
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
            private set
    }

    private fun httpModule() = module {
        single { provideOkHttpClient() }
        single { WebPageRepository(get()) }
    }

    private fun roomModule() = module {
        single {
            val builder = Room.databaseBuilder(context, SakuraDatabase::class.java, "sk_db")
                .addMigrations(SakuraDatabase.MIGRATION_1_2)
            if (BuildConfig.DEBUG) {
                builder.setQueryCallback({ sqlQuery, bindArgs ->
                    Log.d("RoomQuery", "sql: $sqlQuery, args: $bindArgs")
                }, Executors.newSingleThreadExecutor())
            }
            builder.build()
        }

        single {
            get<SakuraDatabase>().getVideoHistoryDao()
        }

        single {
            get<SakuraDatabase>().searchHistoryDao()
        }
    }

    private fun viewModelModule() = module {
        viewModel { holder -> DetailPageViewModel(holder.get(), get(), get()) }
        viewModel { holder -> VideoPlayerViewModel(holder.get(), get(), get()) }
        viewModelOf(::HomeViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::HistoryViewModel)
        viewModelOf(::TimelineViewModel)
        viewModel { holder -> SearchResultViewModel(holder.get(), get()) }

    }

    private fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().connectTimeout(10L, TimeUnit.SECONDS)
            .readTimeout(10L, TimeUnit.SECONDS).addInterceptor(Interceptor { chain ->
                chain.request().newBuilder().header(
                    "user-agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36"
                ).build().let {
                    chain.proceed(it)
                }
            }).apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }.build()
    }

    override fun onTerminate() {
        WebServerContext.stopServer()
        super.onTerminate()
    }

}