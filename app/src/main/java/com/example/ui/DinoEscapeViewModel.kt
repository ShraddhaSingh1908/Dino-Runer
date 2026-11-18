package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEngine
import com.example.data.GamePreferencesRepository
import com.example.data.StoredGameData
import com.example.engine.AtmosphereMode
import com.example.engine.GameHudState
import com.example.engine.GameScreen
import com.example.engine.GameSimulation
import com.example.engine.InputCommand
import com.example.engine.SoundEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DinoEscapeViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = GamePreferencesRepository(application)
  val soundEngine = SoundEngine(application)

  private var storedData = StoredGameData()
  private var runRecorded = false

  val simulation: GameSimulation =
    GameSimulation(
      onSound = { sfx -> soundEngine.play(sfx) },
      onRunEnded = { finalScore, coins -> handleRunEnded(finalScore, coins) },
    )

  private val _hudState = MutableStateFlow(GameHudState())
  val hudState: StateFlow<GameHudState> = _hudState.asStateFlow()

  init {
    soundEngine.init()
    viewModelScope.launch {
      repository.storedData.collect { data ->
        storedData = data
        simulation.atmosphere = data.atmosphere
        soundEngine.setEnabled(data.soundEnabled)
        syncStateFromSimulation()
      }
    }
  }

  fun syncStateFromSimulation() {
    val simScore = simulation.runScoreFloat.toInt()
    val isNewBest =
      simulation.screen == GameScreen.GAME_OVER && simScore > 0 && simScore >= storedData.bestScore
    val shouldPlayAmbience =
      storedData.soundEnabled &&
        (simulation.screen == GameScreen.MAIN_MENU ||
          simulation.screen == GameScreen.COUNTDOWN ||
          simulation.screen == GameScreen.PLAYING)
    soundEngine.setAmbiencePlaying(shouldPlayAmbience)

    _hudState.update {
      it.copy(
        screen = simulation.screen,
        countdownLabel = simulation.getCountdownLabel(),
        score = simScore,
        distanceMeters = simulation.distanceFloat.toInt(),
        runCoins = simulation.runCoins,
        totalCoins = storedData.totalCoins,
        bestScore = storedData.bestScore,
        speedLevel = simulation.speedLevel,
        stumbleLevel = simulation.stumbleLevel,
        activePowerUps = simulation.getActivePowerUpUiList(),
        isNewBest = isNewBest,
        soundEnabled = storedData.soundEnabled,
        atmosphere = storedData.atmosphere,
        touchControlsEnabled = storedData.touchControlsEnabled,
        eventBanner = simulation.eventBanner,
      )
    }
  }

  fun startGame() {
    soundEngine.play(SoundEffect.BUTTON_CLICK)
    runRecorded = false
    simulation.startNewRunCountdown()
    syncStateFromSimulation()
  }

  fun pauseGame() {
    if (simulation.screen == GameScreen.PLAYING) {
      soundEngine.play(SoundEffect.BUTTON_CLICK)
      simulation.pauseRun()
      syncStateFromSimulation()
    }
  }

  fun resumeGame() {
    if (simulation.screen == GameScreen.PAUSED) {
      soundEngine.play(SoundEffect.BUTTON_CLICK)
      simulation.resumeRun()
      syncStateFromSimulation()
    }
  }

  fun restartGame() {
    soundEngine.play(SoundEffect.BUTTON_CLICK)
    runRecorded = false
    simulation.startNewRunCountdown()
    syncStateFromSimulation()
  }

  fun goToMainMenu() {
    soundEngine.play(SoundEffect.BUTTON_CLICK)
    simulation.resetToMenu()
    syncStateFromSimulation()
  }

  fun openHowToPlay() {
    soundEngine.play(SoundEffect.BUTTON_CLICK)
    simulation.screen = GameScreen.HOW_TO_PLAY
    syncStateFromSimulation()
  }

  fun openSettings() {
    soundEngine.play(SoundEffect.BUTTON_CLICK)
    simulation.screen = GameScreen.SETTINGS
    syncStateFromSimulation()
  }

  fun toggleSound() {
    val next = !storedData.soundEnabled
    soundEngine.setEnabled(next)
    if (next) soundEngine.play(SoundEffect.BUTTON_CLICK)
    viewModelScope.launch { repository.setSoundEnabled(next) }
  }

  fun setSoundEnabled(enabled: Boolean) {
    soundEngine.setEnabled(enabled)
    if (enabled) soundEngine.play(SoundEffect.BUTTON_CLICK)
    viewModelScope.launch { repository.setSoundEnabled(enabled) }
  }

  fun setAtmosphere(mode: AtmosphereMode) {
    soundEngine.play(SoundEffect.BUTTON_CLICK)
    simulation.atmosphere = mode
    viewModelScope.launch { repository.setAtmosphere(mode) }
  }

  fun setTouchControlsEnabled(enabled: Boolean) {
    soundEngine.play(SoundEffect.BUTTON_CLICK)
    viewModelScope.launch { repository.setTouchControlsEnabled(enabled) }
  }

  fun sendInput(command: InputCommand) {
    if (simulation.screen == GameScreen.PLAYING) {
      simulation.enqueueInput(command)
    }
  }

  fun onAppPaused() {
    if (simulation.screen == GameScreen.PLAYING) {
      simulation.pauseRun()
      syncStateFromSimulation()
    }
    soundEngine.setAmbiencePlaying(false)
  }

  private fun handleRunEnded(finalScore: Int, coins: Int) {
    if (runRecorded) return
    runRecorded = true
    viewModelScope.launch {
      repository.recordRunResult(finalScore, coins)
      syncStateFromSimulation()
    }
  }

  override fun onCleared() {
    super.onCleared()
    soundEngine.release()
  }
}
