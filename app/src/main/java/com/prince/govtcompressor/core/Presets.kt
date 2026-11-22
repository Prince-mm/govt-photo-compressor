package com.prince.govtcompressor.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "govt_compressor_prefs")

/**
 * Government-portal presets. The defaults below are common values seen on
 * portals — they are STARTING POINTS ONLY and fully editable. The app never
 * claims a portal's current rule; each preset screen carries a reminder to
 * check the portal's latest requirements before uploading.
 */
data class GovPreset(
    val id: String,
    val name: String,
    val targetKb: Int,
    val width: Int,      // px, 0 = keep original
    val height: Int,     // px, 0 = keep original
    val format: OutFormat
)

object Presets {
    val DEFAULTS = listOf(
        GovPreset("voter_photo", "Voter ID Photo", 20, 200, 200, OutFormat.JPEG),
        GovPreset("voter_sign", "Voter ID Signature", 20, 200, 100, OutFormat.JPEG),
        GovPreset("eci", "Election Commission Application", 50, 300, 300, OutFormat.JPEG),
        GovPreset("passport", "Passport Photo", 100, 413, 531, OutFormat.JPEG),
        GovPreset("pan", "PAN Card Application", 50, 213, 213, OutFormat.JPEG),
        GovPreset("aadhaar", "Aadhaar-related Document", 200, 800, 800, OutFormat.JPEG),
        GovPreset("dl", "Driving Licence", 50, 300, 300, OutFormat.JPEG),
        GovPreset("govt_job", "Government Job Application", 100, 400, 400, OutFormat.JPEG)
    )

    private fun kbKey(id: String) = intPreferencesKey("preset_${id}_kb")
    private fun wKey(id: String) = intPreferencesKey("preset_${id}_w")
    private fun hKey(id: String) = intPreferencesKey("preset_${id}_h")
    private fun fmtKey(id: String) = stringPreferencesKey("preset_${id}_fmt")

    fun flow(context: Context, preset: GovPreset): Flow<GovPreset> =
        context.dataStore.data.map { prefs ->
            preset.copy(
                targetKb = prefs[kbKey(preset.id)] ?: preset.targetKb,
                width = prefs[wKey(preset.id)] ?: preset.width,
                height = prefs[hKey(preset.id)] ?: preset.height,
                format = runCatching {
                    OutFormat.valueOf(prefs[fmtKey(preset.id)] ?: preset.format.name)
                }.getOrDefault(preset.format)
            )
        }

    suspend fun save(context: Context, preset: GovPreset) {
        context.dataStore.edit { prefs ->
            prefs[kbKey(preset.id)] = preset.targetKb
            prefs[wKey(preset.id)] = preset.width
            prefs[hKey(preset.id)] = preset.height
            prefs[fmtKey(preset.id)] = preset.format.name
        }
    }

    suspend fun reset(context: Context, preset: GovPreset) {
        context.dataStore.edit { prefs ->
            prefs.remove(kbKey(preset.id))
            prefs.remove(wKey(preset.id))
            prefs.remove(hKey(preset.id))
            prefs.remove(fmtKey(preset.id))
        }
    }

    suspend fun loadAll(context: Context): List<GovPreset> =
        DEFAULTS.map { flow(context, it).first() }

    // ---- Settings ----
    private val DARK_MODE = booleanPreferencesKey("dark_mode")
    private val LAST_TARGET_KB = intPreferencesKey("last_target_kb")

    fun darkModeFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[DARK_MODE] ?: false }

    suspend fun setDarkMode(context: Context, on: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = on }
    }

    suspend fun lastTargetKb(context: Context): Int =
        context.dataStore.data.first()[LAST_TARGET_KB] ?: 50

    suspend fun saveLastTargetKb(context: Context, kb: Int) {
        context.dataStore.edit { it[LAST_TARGET_KB] = kb }
    }
}
