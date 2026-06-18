package com.professional.cam.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 应用设置 DataStore，提供键值对持久化存储。
 *
 * Step 1 范围：仅提供基础的存储能力，后续步骤将扩展更多设置项。
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_ISO = intPreferencesKey("iso")
        private val KEY_EXPOSURE_TIME = intPreferencesKey("exposure_time")
        private val KEY_ZOOM_RATIO = intPreferencesKey("zoom_ratio")
    }

    /** 存储 ISO 值 */
    suspend fun saveIso(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ISO] = value
        }
    }

    /** 读取 ISO 值 */
    val isoFlow: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[KEY_ISO]
    }

    /** 存储曝光时间（微秒） */
    suspend fun saveExposureTime(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXPOSURE_TIME] = value.toInt()
        }
    }

    /** 读取曝光时间 */
    val exposureTimeFlow: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[KEY_EXPOSURE_TIME]?.toLong()
    }

    /** 存储缩放比例（乘以 100 存储为整数） */
    suspend fun saveZoomRatio(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ZOOM_RATIO] = (value * 100).toInt()
        }
    }

    /** 读取缩放比例 */
    val zoomRatioFlow: Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[KEY_ZOOM_RATIO]?.let { it / 100f }
    }
}
