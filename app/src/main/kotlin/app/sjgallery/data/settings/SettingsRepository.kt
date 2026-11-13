package app.sjgallery.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ScaleMode { FIT, FILL }

/**
 * How one slide gives way to the next. To add a new animation, add an entry
 * here, map it in the slideshow's `toContentTransform()`, and label it in the
 * settings screen; nothing else needs to change.
 */
enum class SlideTransition { NONE, CROSSFADE }

data class Settings(
    val slideSeconds: Int = DEFAULT_SLIDE_SECONDS,
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val transition: SlideTransition = SlideTransition.NONE,
    val optimizeOnImport: Boolean = true,
) {
    companion object {
        const val DEFAULT_SLIDE_SECONDS = 5
        const val MIN_SLIDE_SECONDS = 1
        const val MAX_SLIDE_SECONDS = 120
    }
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences. These hold nothing sensitive, so they are stored unencrypted. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            slideSeconds = (prefs[Keys.SLIDE_SECONDS] ?: Settings.DEFAULT_SLIDE_SECONDS)
                .coerceIn(Settings.MIN_SLIDE_SECONDS, Settings.MAX_SLIDE_SECONDS),
            scaleMode = enumValueOrDefault(prefs[Keys.SCALE_MODE], ScaleMode.FIT),
            transition = enumValueOrDefault(prefs[Keys.TRANSITION], SlideTransition.NONE),
            optimizeOnImport = prefs[Keys.OPTIMIZE_ON_IMPORT] ?: true,
        )
    }

    suspend fun setSlideSeconds(seconds: Int) {
        dataStore.edit { it[Keys.SLIDE_SECONDS] = seconds }
    }

    suspend fun setScaleMode(mode: ScaleMode) {
        dataStore.edit { it[Keys.SCALE_MODE] = mode.name }
    }

    suspend fun setTransition(transition: SlideTransition) {
        dataStore.edit { it[Keys.TRANSITION] = transition.name }
    }

    suspend fun setOptimizeOnImport(enabled: Boolean) {
        dataStore.edit { it[Keys.OPTIMIZE_ON_IMPORT] = enabled }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private object Keys {
        val SLIDE_SECONDS = intPreferencesKey("slide_seconds")
        val SCALE_MODE = stringPreferencesKey("scale_mode")
        val TRANSITION = stringPreferencesKey("transition")
        val OPTIMIZE_ON_IMPORT = booleanPreferencesKey("optimize_on_import")
    }
}
