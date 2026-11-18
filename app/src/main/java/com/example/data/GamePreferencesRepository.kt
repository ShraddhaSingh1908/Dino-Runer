package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.AtmosphereMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dinoDataStore by preferencesDataStore(name = "dino_escape_prefs")

data class StoredGameData(
  val bestScore: Int = 0,
  val totalCoins: Int = 0,
  val soundEnabled: Boolean = true,
  val atmosphere: AtmosphereMode = AtmosphereMode.DAYLIGHT,
  val touchControlsEnabled: Boolean = false,
)

class GamePreferencesRepository(private val context: Context) {

  private object Keys {
    val BEST_SCORE = intPreferencesKey("best_score")
    val TOTAL_COINS = intPreferencesKey("total_coins")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val SUNSET_MODE = booleanPreferencesKey("sunset_mode")
    val TOUCH_CONTROLS = booleanPreferencesKey("touch_controls")
  }

  val storedData: Flow<StoredGameData> =
    context.dinoDataStore.data.map { prefs ->
      StoredGameData(
        bestScore = prefs[Keys.BEST_SCORE] ?: 0,
        totalCoins = prefs[Keys.TOTAL_COINS] ?: 0,
        soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
        atmosphere =
          if (prefs[Keys.SUNSET_MODE] == true) AtmosphereMode.SUNSET else AtmosphereMode.DAYLIGHT,
        touchControlsEnabled = prefs[Keys.TOUCH_CONTROLS] ?: false,
      )
    }

  suspend fun recordRunResult(runScore: Int, runCoins: Int) {
    context.dinoDataStore.edit { prefs ->
      val currentBest = prefs[Keys.BEST_SCORE] ?: 0
      if (runScore > currentBest) {
        prefs[Keys.BEST_SCORE] = runScore
      }
      val currentCoins = prefs[Keys.TOTAL_COINS] ?: 0
      prefs[Keys.TOTAL_COINS] = currentCoins + runCoins.coerceAtLeast(0)
    }
  }

  suspend fun setSoundEnabled(enabled: Boolean) {
    context.dinoDataStore.edit { prefs -> prefs[Keys.SOUND_ENABLED] = enabled }
  }

  suspend fun setAtmosphere(mode: AtmosphereMode) {
    context.dinoDataStore.edit { prefs -> prefs[Keys.SUNSET_MODE] = (mode == AtmosphereMode.SUNSET) }
  }

  suspend fun setTouchControlsEnabled(enabled: Boolean) {
    context.dinoDataStore.edit { prefs -> prefs[Keys.TOUCH_CONTROLS] = enabled }
  }
}
