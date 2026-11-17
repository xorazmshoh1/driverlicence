package uz.xumoyun.prava.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uz.xumoyun.core.ads.AdFeatures
import uz.xumoyun.core.ads.AdUnitIds
import uz.xumoyun.prava.BuildConfig
import uz.xumoyun.prava.data.db.PravaDatabase
import uz.xumoyun.prava.data.db.QuizDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): PravaDatabase =
        Room.databaseBuilder(context, PravaDatabase::class.java, "prava.db").build()

    @Provides fun quizDao(db: PravaDatabase): QuizDao = db.quiz()

    @Provides @Singleton
    fun adUnitIds() = AdUnitIds(
        banner = BuildConfig.AD_BANNER,
        interstitial = BuildConfig.AD_INTERSTITIAL,
        rewarded = BuildConfig.AD_REWARDED,
        appOpen = BuildConfig.AD_APPOPEN,
    )

    @Provides @Singleton
    fun adFeatures() = AdFeatures()
}
