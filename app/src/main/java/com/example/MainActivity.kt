package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.GameScreen
import com.example.engine.InputCommand
import com.example.gl.JungleRenderer
import com.example.ui.DinoEscapeRoot
import com.example.ui.DinoEscapeViewModel
import com.example.ui.theme.DinoEscapeTheme

class MainActivity : ComponentActivity() {

  private val viewModel: DinoEscapeViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      DinoEscapeTheme {
        val hudState by viewModel.hudState.collectAsStateWithLifecycle()
        val renderer =
          remember(viewModel) {
            JungleRenderer(viewModel.simulation) { viewModel.syncStateFromSimulation() }
          }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
          modifier =
            Modifier.fillMaxSize()
              .focusRequester(focusRequester)
              .focusable()
              .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                  Key.DirectionLeft,
                  Key.A -> {
                    viewModel.sendInput(InputCommand.MOVE_LEFT)
                    true
                  }
                  Key.DirectionRight,
                  Key.D -> {
                    viewModel.sendInput(InputCommand.MOVE_RIGHT)
                    true
                  }
                  Key.DirectionUp,
                  Key.W,
                  Key.Spacebar -> {
                    viewModel.sendInput(InputCommand.JUMP)
                    true
                  }
                  Key.DirectionDown,
                  Key.S -> {
                    viewModel.sendInput(InputCommand.SLIDE)
                    true
                  }
                  Key.Escape -> {
                    when (hudState.screen) {
                      GameScreen.PLAYING -> {
                        viewModel.pauseGame()
                        true
                      }
                      GameScreen.PAUSED -> {
                        viewModel.resumeGame()
                        true
                      }
                      else -> false
                    }
                  }
                  else -> false
                }
              }
        ) {
          DinoEscapeRoot(
            hudState = hudState,
            renderer = renderer,
            onStartGame = viewModel::startGame,
            onPauseGame = viewModel::pauseGame,
            onResumeGame = viewModel::resumeGame,
            onRestartGame = viewModel::restartGame,
            onMainMenu = viewModel::goToMainMenu,
            onOpenHowToPlay = viewModel::openHowToPlay,
            onOpenSettings = viewModel::openSettings,
            onToggleSound = viewModel::toggleSound,
            onSetAtmosphere = viewModel::setAtmosphere,
            onSetTouchControls = viewModel::setTouchControlsEnabled,
            onInput = viewModel::sendInput,
          )
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    viewModel.onAppPaused()
  }
}
