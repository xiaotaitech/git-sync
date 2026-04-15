package com.gitsync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "gitsync_prefs")

@Singleton
class PrefsRepository @Inject constructor(private val context: Context) {

    private val PAT_KEY = stringPreferencesKey("pat")
    private val DEFAULT_INTERVAL_KEY = intPreferencesKey("default_interval")

    fun getPat(): Flow<String> = context.dataStore.data.map { it[PAT_KEY] ?: "" }

    suspend fun savePat(token: String) {
        context.dataStore.edit { it[PAT_KEY] = token }
    }

    fun getDefaultInterval(): Flow<Int> = context.dataStore.data.map { it[DEFAULT_INTERVAL_KEY] ?: 0 }

    suspend fun saveDefaultInterval(minutes: Int) {
        context.dataStore.edit { it[DEFAULT_INTERVAL_KEY] = minutes }
    }
}
