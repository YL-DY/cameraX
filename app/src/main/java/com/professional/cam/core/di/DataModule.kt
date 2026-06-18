package com.professional.cam.core.di

import android.content.Context
import com.professional.cam.data.local.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖注入模块
 *
 * Step 1 范围：仅提供 [SettingsDataStore]。
 * [VideoFileManager] 将在后续录像步骤中添加。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore {
        return SettingsDataStore(context)
    }
}
