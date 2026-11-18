package com.example.engine

enum class AtmosphereMode {
  DAYLIGHT,
  SUNSET,
}

enum class GameScreen {
  MAIN_MENU,
  HOW_TO_PLAY,
  SETTINGS,
  COUNTDOWN,
  PLAYING,
  PAUSED,
  GAME_OVER,
}

enum class InputCommand {
  MOVE_LEFT,
  MOVE_RIGHT,
  JUMP,
  SLIDE,
}

enum class PowerUpType(val durationSeconds: Float, val displayName: String) {
  MAGNET(8.5f, "Coin Magnet"),
  SHIELD(12.0f, "Jade Shield"),
  SPEED_BOOST(5.5f, "Speed Boost"),
  DOUBLE_COINS(10.0f, "2x Coins"),
}

enum class ObstacleType(val requiresSlide: Boolean, val requiresLaneSwitch: Boolean) {
  FALLEN_TREE(requiresSlide = false, requiresLaneSwitch = false),
  LARGE_ROCK(requiresSlide = false, requiresLaneSwitch = true),
  WOODEN_BARRIER(requiresSlide = false, requiresLaneSwitch = false),
  BROKEN_BRIDGE(requiresSlide = false, requiresLaneSwitch = false),
  DEEP_HOLE(requiresSlide = false, requiresLaneSwitch = false),
  HANGING_BRANCH(requiresSlide = true, requiresLaneSwitch = false),
  STONE_PILLAR(requiresSlide = false, requiresLaneSwitch = true),
  FIRE_PIT(requiresSlide = false, requiresLaneSwitch = false),
  DINO_EGGS(requiresSlide = false, requiresLaneSwitch = false),
  COLLAPSING_PLATFORM(requiresSlide = false, requiresLaneSwitch = false),
}

enum class SegmentTheme {
  JUNGLE_TRAIL,
  TEMPLE_RUINS,
  WOODEN_BRIDGE,
  STONE_BRIDGE_STREAM,
  CANYON_FOSSIL,
}

data class ActivePowerUpUi(
  val type: PowerUpType,
  val remainingSeconds: Float,
  val totalSeconds: Float,
)

data class GameHudState(
  val screen: GameScreen = GameScreen.MAIN_MENU,
  val countdownLabel: String = "3",
  val score: Int = 0,
  val distanceMeters: Int = 0,
  val runCoins: Int = 0,
  val totalCoins: Int = 0,
  val bestScore: Int = 0,
  val speedLevel: Int = 1,
  val stumbleLevel: Int = 0,
  val activePowerUps: List<ActivePowerUpUi> = emptyList(),
  val isNewBest: Boolean = false,
  val soundEnabled: Boolean = true,
  val atmosphere: AtmosphereMode = AtmosphereMode.DAYLIGHT,
  val touchControlsEnabled: Boolean = false,
  val eventBanner: String? = null,
)

enum class SoundEffect {
  BUTTON_CLICK,
  COIN,
  JUMP,
  SLIDE,
  COLLISION,
  DINO_ROAR,
  POWER_UP,
  FOOTSTEP,
  GAME_OVER,
}

class TrackSegment {
  var index: Int = 0
  var zStart: Float = 0f
  var theme: SegmentTheme = SegmentTheme.JUNGLE_TRAIL
  var hasArchway: Boolean = false
  var hasStream: Boolean = false
  var leftTreeVariant: Int = 0
  var rightTreeVariant: Int = 1
  var footprintLane: Int = 0
  var vineOffset: Float = 0f
}

class ObstacleInstance {
  var active: Boolean = false
  var type: ObstacleType = ObstacleType.FALLEN_TREE
  var lane: Int = 0
  var z: Float = 0f
  var collapseProgress: Float = 0f
  var hitProcessed: Boolean = false
}

class CoinInstance {
  var active: Boolean = false
  var x: Float = 0f
  var y: Float = 0.8f
  var z: Float = 0f
  var rotation: Float = 0f
  var magnetPulling: Boolean = false
}

class PowerUpInstance {
  var active: Boolean = false
  var type: PowerUpType = PowerUpType.MAGNET
  var lane: Int = 0
  var z: Float = 0f
  var rotation: Float = 0f
  var bobPhase: Float = 0f
}

class ParticleInstance {
  var active: Boolean = false
  var x: Float = 0f
  var y: Float = 0f
  var z: Float = 0f
  var vx: Float = 0f
  var vy: Float = 0f
  var vz: Float = 0f
  var life: Float = 0f
  var maxLife: Float = 0.5f
  var size: Float = 0.14f
  var r: Float = 1f
  var g: Float = 0.85f
  var b: Float = 0.2f
}
