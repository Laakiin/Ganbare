package fr.lkn.ganbare.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import fr.lkn.ganbare.core.db.AppDatabase
import fr.lkn.ganbare.core.prefs.PreferencesManager
import fr.lkn.ganbare.feature.tasks.data.TaskDao
import fr.lkn.ganbare.domain.calendar.CalendarRepository
import fr.lkn.ganbare.domain.calendar.CalendarRepositoryImpl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "ganbare.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides @Singleton
    fun providePrefs(@ApplicationContext ctx: Context): PreferencesManager =
        PreferencesManager(ctx)

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton
    fun provideCalendarRepository(@ApplicationContext ctx: Context): CalendarRepository =
        CalendarRepositoryImpl(ctx)
}
