package com.example.ui

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.engine.ActivePowerUpUi
import com.example.engine.AtmosphereMode
import com.example.engine.GameHudState
import com.example.engine.GameScreen
import com.example.engine.InputCommand
import com.example.engine.PowerUpType
import com.example.gl.JungleRenderer
import com.example.ui.theme.CanopyCard
import com.example.ui.theme.CanopyNight
import com.example.ui.theme.CanopySurface
import com.example.ui.theme.JungleJade
import com.example.ui.theme.JungleJadeBright
import com.example.ui.theme.MagmaCoral
import com.example.ui.theme.MagnetAmber
import com.example.ui.theme.Sandstone
import com.example.ui.theme.SandstoneMuted
import com.example.ui.theme.ShieldCyan
import com.example.ui.theme.SpeedViolet
import com.example.ui.theme.TreasureGold
import com.example.ui.theme.TreasureGoldSoft
import kotlin.math.abs

@Composable
fun DinoEscapeRoot(
  hudState: GameHudState,
  renderer: JungleRenderer,
  onStartGame: () -> Unit,
  onPauseGame: () -> Unit,
  onResumeGame: () -> Unit,
  onRestartGame: () -> Unit,
  onMainMenu: () -> Unit,
  onOpenHowToPlay: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleSound: () -> Unit,
  onSetAtmosphere: (AtmosphereMode) -> Unit,
  onSetTouchControls: (Boolean) -> Unit,
  onInput: (InputCommand) -> Unit,
) {
  var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      glViewRef?.queueEvent { renderer.dispose() }
      glViewRef?.onPause()
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(CanopyNight)) {
    // 3D OpenGL ES Scene Viewport
    AndroidView(
      factory = { context ->
        GLSurfaceView(context).apply {
          setEGLContextClientVersion(2)
          setRenderer(renderer)
          renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
          glViewRef = this
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    // Danger vignette when T-Rex is lunging right behind the explorer
    if (hudState.screen == GameScreen.PLAYING && hudState.stumbleLevel > 0) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(Color.Transparent, MagmaCoral.copy(alpha = 0.42f)),
                radius = 900f,
              )
            )
      )
    }

    // Gesture capture surface during active gameplay
    if (hudState.screen == GameScreen.PLAYING) {
      SwipeGestureLayer(onInput = onInput)
    }

    // Screen Overlays
    when (hudState.screen) {
      GameScreen.MAIN_MENU ->
        MainMenuOverlay(
          hudState = hudState,
          onPlay = onStartGame,
          onHowToPlay = onOpenHowToPlay,
          onSettings = onOpenSettings,
          onToggleSound = onToggleSound,
        )
      GameScreen.HOW_TO_PLAY -> {
        BackHandler(onBack = onMainMenu)
        HowToPlayOverlay(onBack = onMainMenu)
      }
      GameScreen.SETTINGS -> {
        BackHandler(onBack = onMainMenu)
        SettingsOverlay(
          hudState = hudState,
          onToggleSound = onToggleSound,
          onSetAtmosphere = onSetAtmosphere,
          onSetTouchControls = onSetTouchControls,
          onBack = onMainMenu,
        )
      }
      GameScreen.COUNTDOWN -> {
        BackHandler(onBack = onPauseGame)
        CountdownOverlay(label = hudState.countdownLabel)
      }
      GameScreen.PLAYING -> {
        BackHandler(onBack = onPauseGame)
        GameplayHudOverlay(hudState = hudState, onPause = onPauseGame, onInput = onInput)
      }
      GameScreen.PAUSED -> {
        BackHandler(onBack = onResumeGame)
        PauseOverlay(
          hudState = hudState,
          onResume = onResumeGame,
          onRestart = onRestartGame,
          onToggleSound = onToggleSound,
          onMainMenu = onMainMenu,
        )
      }
      GameScreen.GAME_OVER -> {
        BackHandler(onBack = onMainMenu)
        GameOverOverlay(
          hudState = hudState,
          onRestart = onRestartGame,
          onMainMenu = onMainMenu,
        )
      }
    }
  }
}

@Composable
private fun SwipeGestureLayer(onInput: (InputCommand) -> Unit) {
  var dragX by remember { mutableFloatStateOf(0f) }
  var dragY by remember { mutableFloatStateOf(0f) }
  var fired by remember { mutableStateOf(false) }

  Box(
    modifier =
      Modifier.fillMaxSize().pointerInput(Unit) {
        detectDragGestures(
          onDragStart = {
            dragX = 0f
            dragY = 0f
            fired = false
          },
          onDragEnd = {
            dragX = 0f
            dragY = 0f
            fired = false
          },
          onDragCancel = {
            dragX = 0f
            dragY = 0f
            fired = false
          },
          onDrag = { change, dragAmount ->
            change.consume()
            if (fired) return@detectDragGestures
            dragX += dragAmount.x
            dragY += dragAmount.y
            val threshold = 42f
            if (abs(dragX) > threshold || abs(dragY) > threshold) {
              fired = true
              if (abs(dragX) > abs(dragY)) {
                onInput(if (dragX > 0f) InputCommand.MOVE_RIGHT else InputCommand.MOVE_LEFT)
              } else {
                onInput(if (dragY < 0f) InputCommand.JUMP else InputCommand.SLIDE)
              }
            }
          },
        )
      }
  )
}

@Composable
private fun MainMenuOverlay(
  hudState: GameHudState,
  onPlay: () -> Unit,
  onHowToPlay: () -> Unit,
  onSettings: () -> Unit,
  onToggleSound: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                CanopyNight.copy(alpha = 0.78f),
                Color.Transparent,
                CanopyNight.copy(alpha = 0.88f),
              )
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    // Top bar: Best Score, Total Coins, Sound Toggle
    Row(
      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill(
          icon = Icons.Filled.EmojiEvents,
          iconTint = TreasureGold,
          label = stringResource(R.string.best_score_label),
          value = hudState.bestScore.toString(),
        )
        StatPill(
          icon = Icons.Filled.MonetizationOn,
          iconTint = TreasureGoldSoft,
          label = stringResource(R.string.total_coins_label),
          value = hudState.totalCoins.toString(),
        )
      }

      FilledTonalIconButton(
        onClick = onToggleSound,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("menu_sound_toggle"),
        colors =
          IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = CanopyCard,
            contentColor = Sandstone,
          ),
      ) {
        Icon(
          imageVector =
            if (hudState.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp
            else Icons.AutoMirrored.Filled.VolumeOff,
          contentDescription = stringResource(R.string.cd_sound_toggle),
        )
      }
    }

    // Hero Title & Action Stack
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .widthIn(max = 460.dp)
          .fillMaxWidth()
          .padding(bottom = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "DINO ESCAPE",
        style = MaterialTheme.typography.displayLarge,
        color = TreasureGoldSoft,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.game_subtitle),
        style = MaterialTheme.typography.labelLarge,
        color = JungleJadeBright,
        letterSpacing = 4.sp,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(28.dp))

      Button(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().height(60.dp).testTag("play_button"),
        shape = RoundedCornerShape(18.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = TreasureGold,
            contentColor = CanopyNight,
          ),
      ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.play_action),
          style = MaterialTheme.typography.titleLarge,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onHowToPlay,
          modifier = Modifier.weight(1f).height(52.dp).testTag("how_to_play_button"),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.5.dp, JungleJadeBright.copy(alpha = 0.65f)),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = CanopyCard,
              contentColor = Sandstone,
            ),
        ) {
          Icon(Icons.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.how_to_play),
            style = MaterialTheme.typography.labelLarge,
          )
        }

        OutlinedButton(
          onClick = onSettings,
          modifier = Modifier.weight(1f).height(52.dp).testTag("settings_button"),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.5.dp, SandstoneMuted.copy(alpha = 0.45f)),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = CanopyCard,
              contentColor = Sandstone,
            ),
        ) {
          Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun CountdownOverlay(label: String) {
  val scale by
    animateFloatAsState(
      targetValue = if (label == "RUN!") 1.18f else 1.0f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "countdownScale",
    )

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = CanopyNight.copy(alpha = 0.72f),
      border = BorderStroke(2.dp, TreasureGold.copy(alpha = 0.85f)),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = (56 * scale).sp),
        color = if (label == "RUN!") JungleJadeBright else TreasureGoldSoft,
        modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
      )
    }
  }
}

@Composable
private fun GameplayHudOverlay(
  hudState: GameHudState,
  onPause: () -> Unit,
  onInput: (InputCommand) -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        // Left: Score & Distance
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = CanopyCard,
          border = BorderStroke(1.dp, JungleJade.copy(alpha = 0.55f)),
        ) {
          Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
              text = hudState.score.toString().padStart(6, '0'),
              style = MaterialTheme.typography.titleLarge,
              color = Sandstone,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${hudState.distanceMeters}m",
                style = MaterialTheme.typography.labelLarge,
                color = JungleJadeBright,
              )
              Text(
                text = "LVL ${hudState.speedLevel}",
                style = MaterialTheme.typography.labelSmall,
                color = SandstoneMuted,
              )
            }
          }
        }

        // Right: Coins + Pause Button
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = CanopyCard,
            border = BorderStroke(1.dp, TreasureGold.copy(alpha = 0.6f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                Icons.Filled.MonetizationOn,
                contentDescription = null,
                tint = TreasureGold,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = hudState.runCoins.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = TreasureGoldSoft,
              )
            }
          }

          FilledTonalIconButton(
            onClick = onPause,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("pause_button"),
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = CanopyCard,
                contentColor = Sandstone,
              ),
          ) {
            Icon(
              imageVector = Icons.Filled.Pause,
              contentDescription = stringResource(R.string.cd_pause),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Active Power-Up Chips
      if (hudState.activePowerUps.isNotEmpty()) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          hudState.activePowerUps.forEach { powerUp -> PowerUpHudChip(powerUp) }
        }
      }

      // Dynamic Event / Danger Banner
      AnimatedVisibility(
        visible = hudState.eventBanner != null || hudState.stumbleLevel > 0,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
      ) {
        val text =
          hudState.eventBanner ?: stringResource(R.string.trex_danger_warning)
        val isDanger = hudState.stumbleLevel > 0
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = (if (isDanger) MagmaCoral else JungleJade).copy(alpha = 0.9f),
        ) {
          Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          )
        }
      }
    }

    // Optional visible Touch Controls Pad for mobile players
    if (hudState.touchControlsEnabled) {
      TouchControlsPad(
        onInput = onInput,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
      )
    }
  }
}

@Composable
private fun PowerUpHudChip(powerUp: ActivePowerUpUi) {
  val (icon, tint) =
    when (powerUp.type) {
      PowerUpType.MAGNET -> Icons.Filled.Attractions to MagnetAmber
      PowerUpType.SHIELD -> Icons.Filled.Shield to ShieldCyan
      PowerUpType.SPEED_BOOST -> Icons.Filled.Bolt to SpeedViolet
      PowerUpType.DOUBLE_COINS -> Icons.Filled.Star to TreasureGoldSoft
    }
  val progress = (powerUp.remainingSeconds / powerUp.totalSeconds).coerceIn(0f, 1f)

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = CanopyCard,
    border = BorderStroke(1.dp, tint.copy(alpha = 0.7f)),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).width(92.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
          text = "${powerUp.remainingSeconds.toInt() + 1}s",
          style = MaterialTheme.typography.labelSmall,
          color = Sandstone,
        )
      }
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
        color = tint,
        trackColor = CanopyNight,
      )
    }
  }
}

@Composable
private fun TouchControlsPad(onInput: (InputCommand) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Bottom,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TouchPadButton(
        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        contentDesc = stringResource(R.string.cd_move_left),
        testTag = "touch_left_button",
        onClick = { onInput(InputCommand.MOVE_LEFT) },
      )
      TouchPadButton(
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDesc = stringResource(R.string.cd_move_right),
        testTag = "touch_right_button",
        onClick = { onInput(InputCommand.MOVE_RIGHT) },
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TouchPadButton(
        icon = Icons.Filled.KeyboardArrowDown,
        contentDesc = stringResource(R.string.cd_slide),
        testTag = "touch_slide_button",
        onClick = { onInput(InputCommand.SLIDE) },
      )
      TouchPadButton(
        icon = Icons.Filled.KeyboardArrowUp,
        contentDesc = stringResource(R.string.cd_jump),
        testTag = "touch_jump_button",
        onClick = { onInput(InputCommand.JUMP) },
      )
    }
  }
}

@Composable
private fun TouchPadButton(
  icon: ImageVector,
  contentDesc: String,
  testTag: String,
  onClick: () -> Unit,
) {
  FilledTonalIconButton(
    onClick = onClick,
    modifier =
      Modifier.size(58.dp)
        .border(1.5.dp, SandstoneMuted.copy(alpha = 0.45f), CircleShape)
        .testTag(testTag),
    colors =
      IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = CanopyCard.copy(alpha = 0.85f),
        contentColor = Sandstone,
      ),
  ) {
    Icon(icon, contentDescription = contentDesc, modifier = Modifier.size(32.dp))
  }
}

@Composable
private fun PauseOverlay(
  hudState: GameHudState,
  onResume: () -> Unit,
  onRestart: () -> Unit,
  onToggleSound: () -> Unit,
  onMainMenu: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().background(CanopyNight.copy(alpha = 0.72f)).padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = CanopySurface),
      border = BorderStroke(1.5.dp, JungleJade.copy(alpha = 0.7f)),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.paused_title),
          style = MaterialTheme.typography.headlineMedium,
          color = TreasureGoldSoft,
        )
        Text(
          text = "${hudState.score} pts  •  ${hudState.distanceMeters}m",
          style = MaterialTheme.typography.titleMedium,
          color = SandstoneMuted,
        )
        Spacer(modifier = Modifier.height(6.dp))

        Button(
          onClick = onResume,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("resume_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = JungleJadeBright, contentColor = CanopyNight),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.resume_action), style = MaterialTheme.typography.labelLarge)
        }

        OutlinedButton(
          onClick = onRestart,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pause_restart_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.restart_action), style = MaterialTheme.typography.labelLarge)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.sound_effects_label),
            style = MaterialTheme.typography.bodyLarge,
            color = Sandstone,
          )
          Switch(
            checked = hudState.soundEnabled,
            onCheckedChange = { onToggleSound() },
            modifier = Modifier.testTag("pause_sound_toggle"),
            colors = SwitchDefaults.colors(checkedThumbColor = CanopyNight, checkedTrackColor = TreasureGold),
          )
        }

        OutlinedButton(
          onClick = onMainMenu,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pause_menu_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Text(stringResource(R.string.main_menu_action), style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
private fun GameOverOverlay(
  hudState: GameHudState,
  onRestart: () -> Unit,
  onMainMenu: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, CanopyNight.copy(alpha = 0.85f), CanopyNight)
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
    contentAlignment = Alignment.BottomCenter,
  ) {
    Card(
      modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = CanopySurface.copy(alpha = 0.96f)),
      border = BorderStroke(1.5.dp, MagmaCoral.copy(alpha = 0.75f)),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (hudState.isNewBest) {
          Surface(shape = RoundedCornerShape(12.dp), color = TreasureGold) {
            Text(
              text = stringResource(R.string.new_best_badge),
              style = MaterialTheme.typography.labelLarge,
              color = CanopyNight,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
          }
        }

        Text(
          text = stringResource(R.string.game_over_title),
          style = MaterialTheme.typography.displayMedium,
          color = MagmaCoral,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SummaryMetricBox(
            label = stringResource(R.string.score_label),
            value = hudState.score.toString(),
            accent = Sandstone,
            modifier = Modifier.weight(1f),
          )
          SummaryMetricBox(
            label = stringResource(R.string.distance_label),
            value = "${hudState.distanceMeters}m",
            accent = JungleJadeBright,
            modifier = Modifier.weight(1f),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SummaryMetricBox(
            label = stringResource(R.string.coins_label),
            value = "+${hudState.runCoins}",
            accent = TreasureGoldSoft,
            modifier = Modifier.weight(1f),
          )
          SummaryMetricBox(
            label = stringResource(R.string.best_score_label),
            value = hudState.bestScore.toString(),
            accent = TreasureGold,
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = onRestart,
          modifier = Modifier.fillMaxWidth().height(56.dp).testTag("gameover_restart_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = CanopyNight),
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.restart_action), style = MaterialTheme.typography.titleMedium)
        }

        OutlinedButton(
          onClick = onMainMenu,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("gameover_menu_button"),
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(stringResource(R.string.main_menu_action), style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
private fun HowToPlayOverlay(onBack: () -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(CanopyNight.copy(alpha = 0.88f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = CanopySurface),
      border = BorderStroke(1.5.dp, JungleJade.copy(alpha = 0.65f)),
    ) {
      Column(
        modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("howto_back_button"),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.back_action),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = stringResource(R.string.how_to_play),
            style = MaterialTheme.typography.headlineMedium,
            color = TreasureGoldSoft,
          )
        }

        GuideSection(
          title = "SWIPE & KEYBOARD CONTROLS",
          lines =
            listOf(
              "Swipe Left / Right (or A / D / Arrow Keys): Switch between the 3 jungle lanes.",
              "Swipe Up (or W / Space / Up Arrow): Leap over fallen trees, fire pits, eggs, and gaps.",
              "Swipe Down (or S / Down Arrow): Slide underneath hanging jungle branches. Swiping down mid-air fast-falls!",
              "Escape / Pause Icon: Freeze the chase anytime.",
            ),
        )

        GuideSection(
          title = "SURVIVE THE T-REX",
          lines =
            listOf(
              "Minor stumbles bring the T-Rex right onto your heels. Keep running cleanly to regain distance.",
              "Hitting a stone pillar, boulder, pit, or stumbling twice lets the T-Rex catch you.",
            ),
        )

        GuideSection(
          title = "POWER-UPS",
          lines =
            listOf(
              "Coin Magnet: Pulls nearby gold coins across all lanes.",
              "Jade Shield: Absorbs one obstacle collision safely.",
              "Speed Boost: Surges forward and smashes through hazards.",
              "2x Coins: Doubles every coin collected.",
            ),
        )
      }
    }
  }
}

@Composable
private fun SettingsOverlay(
  hudState: GameHudState,
  onToggleSound: () -> Unit,
  onSetAtmosphere: (AtmosphereMode) -> Unit,
  onSetTouchControls: (Boolean) -> Unit,
  onBack: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(CanopyNight.copy(alpha = 0.88f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = CanopySurface),
      border = BorderStroke(1.5.dp, TreasureGold.copy(alpha = 0.55f)),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.back_action),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TreasureGoldSoft,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.sound_effects_label),
            style = MaterialTheme.typography.titleMedium,
            color = Sandstone,
          )
          Switch(
            checked = hudState.soundEnabled,
            onCheckedChange = { onToggleSound() },
            modifier = Modifier.testTag("settings_sound_switch"),
            colors = SwitchDefaults.colors(checkedThumbColor = CanopyNight, checkedTrackColor = TreasureGold),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.touch_controls_label),
            style = MaterialTheme.typography.titleMedium,
            color = Sandstone,
          )
          Switch(
            checked = hudState.touchControlsEnabled,
            onCheckedChange = onSetTouchControls,
            modifier = Modifier.testTag("settings_touch_switch"),
            colors = SwitchDefaults.colors(checkedThumbColor = CanopyNight, checkedTrackColor = JungleJadeBright),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = stringResource(R.string.atmosphere_label),
            style = MaterialTheme.typography.titleMedium,
            color = Sandstone,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            AtmosphereChoiceButton(
              label = stringResource(R.string.atmosphere_daylight),
              icon = Icons.Filled.WbSunny,
              selected = hudState.atmosphere == AtmosphereMode.DAYLIGHT,
              onClick = { onSetAtmosphere(AtmosphereMode.DAYLIGHT) },
              modifier = Modifier.weight(1f).testTag("atmosphere_daylight"),
            )
            AtmosphereChoiceButton(
              label = stringResource(R.string.atmosphere_sunset),
              icon = Icons.Filled.WbTwilight,
              selected = hudState.atmosphere == AtmosphereMode.SUNSET,
              onClick = { onSetAtmosphere(AtmosphereMode.SUNSET) },
              modifier = Modifier.weight(1f).testTag("atmosphere_sunset"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AtmosphereChoiceButton(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.height(52.dp),
    shape = RoundedCornerShape(14.dp),
    border =
      BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) TreasureGold else SandstoneMuted.copy(alpha = 0.4f),
      ),
    colors =
      ButtonDefaults.outlinedButtonColors(
        containerColor = if (selected) TreasureGold.copy(alpha = 0.16f) else CanopyCard,
        contentColor = if (selected) TreasureGoldSoft else SandstoneMuted,
      ),
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun StatPill(icon: ImageVector, iconTint: Color, label: String, value: String) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = CanopyCard,
    border = BorderStroke(1.dp, SandstoneMuted.copy(alpha = 0.25f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
      Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SandstoneMuted)
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = Sandstone)
      }
    }
  }
}

@Composable
private fun SummaryMetricBox(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = CanopyNight.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = SandstoneMuted)
      Text(text = value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun GuideSection(title: String, lines: List<String>) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = CanopyNight.copy(alpha = 0.55f),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(text = title, style = MaterialTheme.typography.labelLarge, color = JungleJadeBright)
      lines.forEach { line ->
        Text(text = "• $line", style = MaterialTheme.typography.bodyLarge, color = Sandstone)
      }
    }
  }
}
