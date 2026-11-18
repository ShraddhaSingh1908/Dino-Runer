package com.example.engine

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameSimulation(
  private val onSound: (SoundEffect) -> Unit = {},
  private val onRunEnded: (finalScore: Int, runCoins: Int) -> Unit = { _, _ -> },
) {
  companion object {
    const val LANE_WIDTH = 2.4f
    const val SEGMENT_LENGTH = 14.0f
    const val SEGMENT_COUNT = 16
    const val OBSTACLE_POOL_SIZE = 44
    const val COIN_POOL_SIZE = 96
    const val POWERUP_POOL_SIZE = 8
    const val PARTICLE_POOL_SIZE = 84

    const val BASE_SPEED = 15.5f
    const val MAX_SPEED = 32.5f
    const val JUMP_VELOCITY = 14.4f
    const val GRAVITY = -38.0f
    const val SLIDE_DURATION = 0.68f
    const val SAFE_TREX_DISTANCE = 5.4f
    const val CLOSE_TREX_DISTANCE = 2.45f
    const val CATCH_TREX_DISTANCE = 0.72f
  }

  private val random = Random(System.currentTimeMillis())
  private val inputQueue = ConcurrentLinkedQueue<InputCommand>()

  // Pooled scene objects
  val segments = Array(SEGMENT_COUNT) { TrackSegment() }
  val obstacles = Array(OBSTACLE_POOL_SIZE) { ObstacleInstance() }
  val coins = Array(COIN_POOL_SIZE) { CoinInstance() }
  val powerUps = Array(POWERUP_POOL_SIZE) { PowerUpInstance() }
  val particles = Array(PARTICLE_POOL_SIZE) { ParticleInstance() }

  // Simulation screen & run state
  @Volatile var screen: GameScreen = GameScreen.MAIN_MENU
  var atmosphere: AtmosphereMode = AtmosphereMode.DAYLIGHT

  // Explorer state
  var targetLane: Int = 0
  var playerX: Float = 0f
  var playerY: Float = 0f
  var playerVy: Float = 0f
  var isJumping: Boolean = false
  var slideTimer: Float = 0f
  var laneLean: Float = 0f
  var runAnimPhase: Float = 0f
  var invulnTimer: Float = 0f
  var stumbleLevel: Int = 0
  var stumbleRecoveryTimer: Float = 0f
  var fallDeathTimer: Float = 0f
  var fallDeathType: Int = 0 // 0 = caught by T-Rex, 1 = pit fall

  // Buffered input for responsive controls
  private var bufferedJumpTimer: Float = 0f
  private var bufferedSlideTimer: Float = 0f

  // T-Rex state
  var trexZ: Float = SAFE_TREX_DISTANCE
  var trexX: Float = 0f
  var trexStridePhase: Float = 0f
  var trexRoarTimer: Float = 0f
  var trexMouthOpen: Float = 0.15f
  var trexAttackProgress: Float = 0f

  // Camera & effects state
  var screenShake: Float = 0f
  var cameraFovBoost: Float = 0f
  var menuOrbitTime: Float = 0f

  // Progression & score
  var currentSpeed: Float = BASE_SPEED
  var distanceFloat: Float = 0f
  var runScoreFloat: Float = 0f
  var runCoins: Int = 0
  var speedLevel: Int = 1
  var nextMilestoneMeters: Int = 250
  var eventBanner: String? = null
  private var eventBannerTimer: Float = 0f

  // Active power-ups
  private val activePowerUpTimers = mutableMapOf<PowerUpType, Float>()

  // Spawner tracking
  private var worldScrollZ: Float = 0f
  private var nextObstacleSpawnDist: Float = 26f
  private var nextPowerUpDist: Float = 75f
  private var footstepAccum: Float = 0f
  private var countdownTimer: Float = 0f
  private var lastCountdownBucket: Int = -1
  private var gameOverNotified: Boolean = false

  init {
    resetToMenu()
  }

  fun enqueueInput(command: InputCommand) {
    inputQueue.add(command)
  }

  fun startNewRunCountdown() {
    inputQueue.clear()
    resetWorldGeometry()
    screen = GameScreen.COUNTDOWN
    countdownTimer = 3.2f
    lastCountdownBucket = 4
    trexZ = 9.5f
    trexRoarTimer = 2.6f
    eventBanner = null
    eventBannerTimer = 0f
    gameOverNotified = false
    onSound(SoundEffect.DINO_ROAR)
  }

  fun pauseRun() {
    if (screen == GameScreen.PLAYING) {
      screen = GameScreen.PAUSED
    }
  }

  fun resumeRun() {
    if (screen == GameScreen.PAUSED) {
      inputQueue.clear()
      screen = GameScreen.PLAYING
    }
  }

  fun resetToMenu() {
    inputQueue.clear()
    resetWorldGeometry()
    screen = GameScreen.MAIN_MENU
    trexZ = 4.6f
    trexRoarTimer = 0.8f
  }

  private fun resetWorldGeometry() {
    targetLane = 0
    playerX = 0f
    playerY = 0f
    playerVy = 0f
    isJumping = false
    slideTimer = 0f
    laneLean = 0f
    runAnimPhase = 0f
    invulnTimer = 0f
    stumbleLevel = 0
    stumbleRecoveryTimer = 0f
    fallDeathTimer = 0f
    fallDeathType = 0
    bufferedJumpTimer = 0f
    bufferedSlideTimer = 0f

    trexZ = SAFE_TREX_DISTANCE
    trexX = 0f
    trexStridePhase = 0f
    trexRoarTimer = 0f
    trexMouthOpen = 0.15f
    trexAttackProgress = 0f

    screenShake = 0f
    cameraFovBoost = 0f
    currentSpeed = BASE_SPEED
    distanceFloat = 0f
    runScoreFloat = 0f
    runCoins = 0
    speedLevel = 1
    nextMilestoneMeters = 250
    eventBanner = null
    eventBannerTimer = 0f

    activePowerUpTimers.clear()
    worldScrollZ = 0f
    nextObstacleSpawnDist = 28f
    nextPowerUpDist = 68f
    footstepAccum = 0f

    for (i in segments.indices) {
      val seg = segments[i]
      seg.index = i
      seg.zStart = 18f - i * SEGMENT_LENGTH
      configureSegment(seg, i)
    }
    for (o in obstacles) o.active = false
    for (c in coins) c.active = false
    for (p in powerUps) p.active = false
    for (pt in particles) pt.active = false
  }

  private fun configureSegment(seg: TrackSegment, seqIndex: Int) {
    seg.theme =
      when {
        seqIndex < 3 -> SegmentTheme.JUNGLE_TRAIL
        seqIndex % 9 == 4 -> SegmentTheme.STONE_BRIDGE_STREAM
        seqIndex % 7 == 3 -> SegmentTheme.WOODEN_BRIDGE
        seqIndex % 5 == 2 -> SegmentTheme.TEMPLE_RUINS
        seqIndex % 6 == 5 -> SegmentTheme.CANYON_FOSSIL
        else -> SegmentTheme.JUNGLE_TRAIL
      }
    seg.hasArchway = (seqIndex > 1 && seg.theme == SegmentTheme.TEMPLE_RUINS)
    seg.hasStream = (seg.theme == SegmentTheme.STONE_BRIDGE_STREAM)
    seg.leftTreeVariant = random.nextInt(3)
    seg.rightTreeVariant = random.nextInt(3)
    seg.footprintLane = random.nextInt(-1, 2)
    seg.vineOffset = random.nextFloat() * 6.28f
  }

  fun update(rawDt: Float) {
    val dt = rawDt.coerceIn(0f, 0.05f)

    when (screen) {
      GameScreen.MAIN_MENU,
      GameScreen.HOW_TO_PLAY,
      GameScreen.SETTINGS -> {
        menuOrbitTime += dt
        trexStridePhase += dt * 3.2f
        trexRoarTimer = max(0f, trexRoarTimer - dt)
        if (random.nextFloat() < dt * 0.16f && trexRoarTimer <= 0f) {
          trexRoarTimer = 1.1f
        }
        trexMouthOpen = if (trexRoarTimer > 0f) 0.85f else 0.12f + 0.08f * sin(menuOrbitTime * 2f)
        updateParticles(dt)
      }
      GameScreen.COUNTDOWN -> {
        countdownTimer -= dt
        val bucket =
          when {
            countdownTimer > 2.2f -> 3
            countdownTimer > 1.2f -> 2
            countdownTimer > 0.35f -> 1
            else -> 0
          }
        if (bucket != lastCountdownBucket) {
          lastCountdownBucket = bucket
          if (bucket > 0) onSound(SoundEffect.BUTTON_CLICK) else onSound(SoundEffect.JUMP)
        }
        // Intro chase dramatic surge: player begins jogging and T-Rex charges behind
        val introSpeed = 9.0f
        scrollWorld(introSpeed * dt)
        runAnimPhase += dt * 10f
        trexStridePhase += dt * 8.5f
        trexZ = max(SAFE_TREX_DISTANCE, trexZ - dt * 1.4f)
        trexRoarTimer = max(0f, trexRoarTimer - dt)
        trexMouthOpen = if (trexRoarTimer > 0f) 0.9f else 0.25f
        if (countdownTimer <= 0f) {
          screen = GameScreen.PLAYING
          trexZ = SAFE_TREX_DISTANCE
        }
      }
      GameScreen.PAUSED -> {
        // Everything frozen cleanly while paused
      }
      GameScreen.PLAYING -> {
        stepPlaying(dt)
      }
      GameScreen.GAME_OVER -> {
        fallDeathTimer += dt
        trexAttackProgress = min(1f, trexAttackProgress + dt * 2.2f)
        trexZ = max(CATCH_TREX_DISTANCE, trexZ - dt * 5.0f)
        trexMouthOpen = 0.35f + 0.6f * abs(sin(fallDeathTimer * 6.5f))
        screenShake = max(0f, screenShake - dt * 1.8f)
        updateParticles(dt)
      }
    }
  }

  private fun stepPlaying(dt: Float) {
    processInputs(dt)

    // Speed & difficulty curve
    val distanceFactor = (distanceFloat / 1100f).coerceIn(0f, 1f)
    val baseTargetSpeed = BASE_SPEED + (MAX_SPEED - BASE_SPEED) * distanceFactor
    val boostActive = isPowerUpActive(PowerUpType.SPEED_BOOST)
    currentSpeed = if (boostActive) baseTargetSpeed * 1.45f else baseTargetSpeed
    speedLevel = (1 + (distanceFactor * 9f).toInt()).coerceIn(1, 10)

    val targetFovBoost = if (boostActive) 1.0f else 0f
    cameraFovBoost += (targetFovBoost - cameraFovBoost) * min(1f, dt * 6f)

    // Scroll world & update distance/score
    val dz = currentSpeed * dt
    distanceFloat += dz
    runScoreFloat += dz * (1.2f + speedLevel * 0.15f)

    val distMeters = distanceFloat.toInt()
    if (distMeters >= nextMilestoneMeters) {
      runScoreFloat += 250f
      showBanner("${nextMilestoneMeters}m MILESTONE! +250")
      nextMilestoneMeters += 250
      onSound(SoundEffect.POWER_UP)
    }

    scrollWorld(dz)
    spawnDirector(dz)

    // Update player lateral, vertical, slide physics
    val desiredX = targetLane * LANE_WIDTH
    val dx = desiredX - playerX
    playerX += dx * min(1f, dt * 16f)
    laneLean = (dx * 0.18f).coerceIn(-0.42f, 0.42f)

    if (slideTimer > 0f) {
      slideTimer = max(0f, slideTimer - dt)
    }

    if (isJumping) {
      playerY += playerVy * dt
      playerVy += GRAVITY * dt
      if (playerY <= 0f) {
        playerY = 0f
        playerVy = 0f
        isJumping = false
        spawnBurst(playerX, 0.1f, 0f, 6, 0.72f, 0.64f, 0.48f)
        if (bufferedSlideTimer > 0f) {
          triggerSlide()
          bufferedSlideTimer = 0f
        } else if (bufferedJumpTimer > 0f) {
          triggerJump()
          bufferedJumpTimer = 0f
        }
      }
    } else {
      runAnimPhase += dt * (currentSpeed * 0.72f)
      if (slideTimer <= 0f) {
        footstepAccum += dt * (currentSpeed * 0.145f)
        if (footstepAccum >= 1f) {
          footstepAccum -= 1f
          onSound(SoundEffect.FOOTSTEP)
        }
      }
    }

    if (invulnTimer > 0f) invulnTimer = max(0f, invulnTimer - dt)
    if (eventBannerTimer > 0f) {
      eventBannerTimer -= dt
      if (eventBannerTimer <= 0f) eventBanner = null
    }
    screenShake = max(0f, screenShake - dt * 2.4f)

    // Update power-up timers
    val iter = activePowerUpTimers.entries.iterator()
    while (iter.hasNext()) {
      val entry = iter.next()
      val remaining = entry.value - dt
      if (remaining <= 0f) {
        iter.remove()
      } else {
        entry.setValue(remaining)
      }
    }

    // Update T-Rex pursuit
    updateTRexChase(dt)

    // Move & check collisions for obstacles, coins, power-ups
    updateObstacles(dz, dt)
    updateCoins(dz, dt)
    updatePowerUps(dz, dt)
    updateParticles(dt)
  }

  private fun processInputs(dt: Float) {
    if (bufferedJumpTimer > 0f) bufferedJumpTimer = max(0f, bufferedJumpTimer - dt)
    if (bufferedSlideTimer > 0f) bufferedSlideTimer = max(0f, bufferedSlideTimer - dt)

    while (true) {
      val cmd = inputQueue.poll() ?: break
      when (cmd) {
        InputCommand.MOVE_LEFT -> {
          if (targetLane > -1) targetLane--
        }
        InputCommand.MOVE_RIGHT -> {
          if (targetLane < 1) targetLane++
        }
        InputCommand.JUMP -> {
          if (!isJumping) {
            triggerJump()
          } else {
            bufferedJumpTimer = 0.15f
          }
        }
        InputCommand.SLIDE -> {
          if (isJumping) {
            // Fast-fall into slide
            playerVy = min(playerVy, -24f)
            bufferedSlideTimer = 0.25f
          } else {
            triggerSlide()
          }
        }
      }
    }
  }

  private fun triggerJump() {
    slideTimer = 0f
    isJumping = true
    playerVy = JUMP_VELOCITY
    onSound(SoundEffect.JUMP)
  }

  private fun triggerSlide() {
    slideTimer = SLIDE_DURATION
    onSound(SoundEffect.SLIDE)
  }

  private fun updateTRexChase(dt: Float) {
    trexStridePhase += dt * (currentSpeed * 0.56f)
    trexX += (playerX * 0.72f - trexX) * min(1f, dt * 6f)

    if (stumbleLevel > 0) {
      stumbleRecoveryTimer -= dt
      if (stumbleRecoveryTimer <= 0f) {
        stumbleLevel = 0
        showBanner("ESCAPED T-REX LUNGE!")
      }
    }

    val desiredTrexZ = if (stumbleLevel > 0) CLOSE_TREX_DISTANCE else SAFE_TREX_DISTANCE
    trexZ += (desiredTrexZ - trexZ) * min(1f, dt * 3.5f)

    if (trexRoarTimer > 0f) {
      trexRoarTimer = max(0f, trexRoarTimer - dt)
      trexMouthOpen = 0.9f
    } else {
      trexMouthOpen = 0.18f + 0.14f * abs(sin(trexStridePhase * 0.5f))
    }
  }

  private fun scrollWorld(dz: Float) {
    worldScrollZ += dz
    var minZ = Float.MAX_VALUE
    for (seg in segments) {
      seg.zStart += dz
      if (seg.zStart < minZ) minZ = seg.zStart
    }
    for (seg in segments) {
      if (seg.zStart > 22f) {
        seg.zStart = minZ - SEGMENT_LENGTH
        minZ = seg.zStart
        seg.index += SEGMENT_COUNT
        configureSegment(seg, seg.index)
      }
    }
  }

  private fun spawnDirector(dz: Float) {
    nextObstacleSpawnDist -= dz
    if (nextObstacleSpawnDist <= 0f) {
      spawnObstacleWave(-185f)
      val difficultyRatio = (distanceFloat / 1200f).coerceIn(0f, 1f)
      val minGap = 15.5f - difficultyRatio * 4.5f
      val maxGap = 23.0f - difficultyRatio * 6.0f
      nextObstacleSpawnDist = minGap + random.nextFloat() * (maxGap - minGap)
    }

    nextPowerUpDist -= dz
    if (nextPowerUpDist <= 0f) {
      spawnRandomPowerUp(-192f)
      nextPowerUpDist = 62f + random.nextFloat() * 34f
    }
  }

  private fun spawnObstacleWave(spawnZ: Float) {
    val difficulty = (distanceFloat / 1000f).coerceIn(0f, 1f)
    val patternRoll = random.nextFloat()

    // Guarantee fair patterns: never block all 3 lanes with non-jumpable/non-slideable obstacles
    when {
      patternRoll < 0.34f -> {
        // Single obstacle + coin trail in adjacent safe lane
        val lane = random.nextInt(-1, 2)
        val type = pickObstacleType(allowFullBlock = true)
        activateObstacle(type, lane, spawnZ)
        if (!type.requiresLaneSwitch && !type.requiresSlide && random.nextFloat() < 0.7f) {
          spawnCoinArc(lane, spawnZ)
        } else {
          val safeLane = listOf(-1, 0, 1).filter { it != lane }.random(random)
          spawnCoinLine(safeLane, spawnZ + 4f, 5)
        }
      }
      patternRoll < 0.64f -> {
        // Two-lane obstacle (at least 1 lane completely open)
        val openLane = random.nextInt(-1, 2)
        for (lane in -1..1) {
          if (lane != openLane) {
            val type = pickObstacleType(allowFullBlock = (difficulty > 0.25f))
            activateObstacle(type, lane, spawnZ)
          }
        }
        spawnCoinLine(openLane, spawnZ + 3.5f, 5)
      }
      patternRoll < 0.84f -> {
        // Three-lane jumpable OR three-lane slideable wall (100% fair via jump or slide!)
        val slideWall = random.nextBoolean()
        val type =
          if (slideWall) ObstacleType.HANGING_BRANCH
          else listOf(ObstacleType.FALLEN_TREE, ObstacleType.WOODEN_BARRIER, ObstacleType.FIRE_PIT)
            .random(random)
        for (lane in -1..1) {
          activateObstacle(type, lane, spawnZ)
        }
        val coinLane = random.nextInt(-1, 2)
        if (slideWall) {
          spawnLowSlideCoinLine(coinLane, spawnZ + 2f, 4)
        } else {
          spawnCoinArc(coinLane, spawnZ)
        }
      }
      else -> {
        // Staggered weave: lane A at spawnZ, lane B at spawnZ - 7.5f
        val laneA = random.nextInt(-1, 2)
        val laneB = listOf(-1, 0, 1).filter { it != laneA }.random(random)
        activateObstacle(pickObstacleType(allowFullBlock = true), laneA, spawnZ)
        activateObstacle(pickObstacleType(allowFullBlock = false), laneB, spawnZ - 8.0f)
        val safeLane = listOf(-1, 0, 1).first { it != laneA && it != laneB }
        spawnCoinLine(safeLane, spawnZ, 6)
      }
    }
  }

  private fun pickObstacleType(allowFullBlock: Boolean): ObstacleType {
    val pool =
      if (allowFullBlock) {
        ObstacleType.entries
      } else {
        ObstacleType.entries.filter { !it.requiresLaneSwitch }
      }
    return pool[random.nextInt(pool.size)]
  }

  private fun activateObstacle(type: ObstacleType, lane: Int, z: Float) {
    val slot = obstacles.firstOrNull { !it.active } ?: return
    slot.active = true
    slot.type = type
    slot.lane = lane
    slot.z = z
    slot.collapseProgress = 0f
    slot.hitProcessed = false
  }

  private fun spawnCoinLine(lane: Int, startZ: Float, count: Int) {
    val x = lane * LANE_WIDTH
    for (i in 0 until count) {
      val slot = coins.firstOrNull { !it.active } ?: return
      slot.active = true
      slot.x = x
      slot.y = 0.85f
      slot.z = startZ - i * 2.2f
      slot.rotation = i * 0.45f
      slot.magnetPulling = false
    }
  }

  private fun spawnLowSlideCoinLine(lane: Int, startZ: Float, count: Int) {
    val x = lane * LANE_WIDTH
    for (i in 0 until count) {
      val slot = coins.firstOrNull { !it.active } ?: return
      slot.active = true
      slot.x = x
      slot.y = 0.48f
      slot.z = startZ - i * 2.0f
      slot.rotation = i * 0.45f
      slot.magnetPulling = false
    }
  }

  private fun spawnCoinArc(lane: Int, centerZ: Float) {
    val x = lane * LANE_WIDTH
    val offsets = floatArrayOf(3.2f, 1.6f, 0f, -1.6f, -3.2f)
    val heights = floatArrayOf(1.1f, 2.05f, 2.45f, 2.05f, 1.1f)
    for (i in offsets.indices) {
      val slot = coins.firstOrNull { !it.active } ?: return
      slot.active = true
      slot.x = x
      slot.y = heights[i]
      slot.z = centerZ + offsets[i]
      slot.rotation = i * 0.5f
      slot.magnetPulling = false
    }
  }

  private fun spawnRandomPowerUp(z: Float) {
    val slot = powerUps.firstOrNull { !it.active } ?: return
    slot.active = true
    slot.type = PowerUpType.entries[random.nextInt(PowerUpType.entries.size)]
    slot.lane = random.nextInt(-1, 2)
    slot.z = z
    slot.rotation = 0f
    slot.bobPhase = random.nextFloat() * 6.28f
  }

  private fun updateObstacles(dz: Float, dt: Float) {
    val playerBottom = playerY
    val playerTop = if (slideTimer > 0f) playerY + 0.78f else playerY + 1.85f
    val playerHalfW = 0.46f

    for (obs in obstacles) {
      if (!obs.active) continue
      obs.z += dz

      if (obs.type == ObstacleType.COLLAPSING_PLATFORM && obs.z > -18f) {
        obs.collapseProgress = min(1f, obs.collapseProgress + dt * 1.15f)
      }

      if (obs.z > 12f) {
        obs.active = false
        continue
      }

      if (obs.hitProcessed || invulnTimer > 0f) continue

      // Check Z overlap with player at z = 0
      val obsHalfDepth =
        when (obs.type) {
          ObstacleType.DEEP_HOLE,
          ObstacleType.BROKEN_BRIDGE,
          ObstacleType.COLLAPSING_PLATFORM -> 0.85f
          else -> 0.58f
        }
      if (abs(obs.z) > obsHalfDepth + 0.36f) continue

      // Check X lane overlap
      val obsX = obs.lane * LANE_WIDTH
      val obsHalfW = if (obs.type.requiresLaneSwitch) 0.78f else 0.72f
      if (abs(playerX - obsX) > obsHalfW + playerHalfW) continue

      // Check Y vertical overlap
      val (obsMinY, obsMaxY) =
        when (obs.type) {
          ObstacleType.HANGING_BRANCH -> 1.02f to 3.1f
          ObstacleType.LARGE_ROCK -> 0f to 2.25f
          ObstacleType.STONE_PILLAR -> 0f to 4.2f
          ObstacleType.DEEP_HOLE,
          ObstacleType.BROKEN_BRIDGE,
          ObstacleType.COLLAPSING_PLATFORM -> -1.0f to 0.72f
          ObstacleType.FALLEN_TREE,
          ObstacleType.WOODEN_BARRIER,
          ObstacleType.FIRE_PIT,
          ObstacleType.DINO_EGGS -> 0f to 1.05f
        }

      if (playerTop > obsMinY && playerBottom < obsMaxY) {
        handleObstacleCollision(obs)
      }
    }
  }

  private fun handleObstacleCollision(obs: ObstacleInstance) {
    obs.hitProcessed = true

    if (isPowerUpActive(PowerUpType.SPEED_BOOST)) {
      obs.active = false
      runScoreFloat += 35f
      screenShake = 0.25f
      spawnBurst(obs.lane * LANE_WIDTH, 1.0f, 0f, 14, 1f, 0.55f, 0.15f)
      onSound(SoundEffect.COLLISION)
      return
    }

    if (isPowerUpActive(PowerUpType.SHIELD)) {
      activePowerUpTimers.remove(PowerUpType.SHIELD)
      obs.active = false
      invulnTimer = 1.25f
      screenShake = 0.35f
      showBanner("SHIELD ABSORBED HIT!")
      spawnBurst(playerX, 1.1f, 0f, 18, 0.18f, 0.85f, 0.95f)
      onSound(SoundEffect.COLLISION)
      return
    }

    // Check lethal vs stumble
    val isPitOrHardWall =
      obs.type == ObstacleType.DEEP_HOLE ||
        obs.type == ObstacleType.BROKEN_BRIDGE ||
        obs.type == ObstacleType.STONE_PILLAR ||
        obs.type == ObstacleType.LARGE_ROCK

    if (stumbleLevel == 0 && !isPitOrHardWall) {
      // First minor hit: stumble & bring T-Rex dangerously close!
      stumbleLevel = 1
      stumbleRecoveryTimer = 8.5f
      invulnTimer = 1.1f
      trexRoarTimer = 1.4f
      screenShake = 0.6f
      showBanner("STUMBLED! T-REX IS RIGHT BEHIND YOU!")
      spawnBurst(playerX, 0.9f, 0f, 14, 0.92f, 0.3f, 0.18f)
      onSound(SoundEffect.COLLISION)
      onSound(SoundEffect.DINO_ROAR)
    } else {
      // Fatal hit or second strike while stumbling -> T-Rex catches explorer!
      triggerGameOver(
        pitFall = (obs.type == ObstacleType.DEEP_HOLE || obs.type == ObstacleType.BROKEN_BRIDGE)
      )
    }
  }

  private fun triggerGameOver(pitFall: Boolean) {
    screen = GameScreen.GAME_OVER
    fallDeathTimer = 0f
    fallDeathType = if (pitFall) 1 else 0
    trexAttackProgress = 0f
    trexRoarTimer = 1.8f
    screenShake = 0.95f
    eventBanner = null
    spawnBurst(playerX, 1.0f, 0f, 22, 0.95f, 0.35f, 0.18f)
    onSound(SoundEffect.COLLISION)
    onSound(SoundEffect.GAME_OVER)
    if (!gameOverNotified) {
      gameOverNotified = true
      onRunEnded(runScoreFloat.toInt(), runCoins)
    }
  }

  private fun updateCoins(dz: Float, dt: Float) {
    val magnetActive = isPowerUpActive(PowerUpType.MAGNET)
    val doubleActive = isPowerUpActive(PowerUpType.DOUBLE_COINS)
    val playerMidY = if (slideTimer > 0f) playerY + 0.45f else playerY + 1.0f

    for (coin in coins) {
      if (!coin.active) continue
      coin.z += dz
      coin.rotation += dt * 4.5f

      if (coin.z > 10f) {
        coin.active = false
        continue
      }

      val dx = playerX - coin.x
      val dy = playerMidY - coin.y
      val dzDist = -coin.z
      val distSq = dx * dx + dy * dy + dzDist * dzDist

      if (magnetActive && (coin.magnetPulling || (distSq < 72f && coin.z < 2.0f))) {
        coin.magnetPulling = true
        val pullRate = min(1f, dt * 12f)
        coin.x += dx * pullRate
        coin.y += dy * pullRate
        coin.z += dzDist * pullRate
      }

      if (distSq < 1.35f) {
        coin.active = false
        val value = if (doubleActive) 2 else 1
        runCoins += value
        runScoreFloat += value * 25f
        spawnBurst(coin.x, coin.y, 0f, 8, 1f, 0.82f, 0.1f)
        onSound(SoundEffect.COIN)
      }
    }
  }

  private fun updatePowerUps(dz: Float, dt: Float) {
    val playerMidY = if (slideTimer > 0f) playerY + 0.45f else playerY + 1.0f
    for (pu in powerUps) {
      if (!pu.active) continue
      pu.z += dz
      pu.rotation += dt * 2.6f
      pu.bobPhase += dt * 3.8f

      if (pu.z > 10f) {
        pu.active = false
        continue
      }

      val puX = pu.lane * LANE_WIDTH
      val puY = 1.05f + 0.2f * sin(pu.bobPhase)
      val dx = playerX - puX
      val dy = playerMidY - puY
      val distSq = dx * dx + dy * dy + pu.z * pu.z

      if (distSq < 1.65f) {
        pu.active = false
        activePowerUpTimers[pu.type] = pu.type.durationSeconds
        runScoreFloat += 50f
        showBanner("${pu.type.displayName.uppercase()}!")
        spawnBurst(puX, puY, 0f, 16, 0.3f, 0.95f, 0.85f)
        onSound(SoundEffect.POWER_UP)
      }
    }
  }

  private fun spawnBurst(x: Float, y: Float, z: Float, count: Int, r: Float, g: Float, b: Float) {
    var spawned = 0
    for (pt in particles) {
      if (pt.active) continue
      val angle = random.nextFloat() * (2f * PI.toFloat())
      val speed = 2.0f + random.nextFloat() * 4.5f
      pt.active = true
      pt.x = x
      pt.y = y
      pt.z = z
      pt.vx = cos(angle) * speed
      pt.vy = 2.2f + random.nextFloat() * 4.5f
      pt.vz = sin(angle) * speed
      pt.maxLife = 0.35f + random.nextFloat() * 0.25f
      pt.life = pt.maxLife
      pt.size = 0.10f + random.nextFloat() * 0.10f
      pt.r = r
      pt.g = g
      pt.b = b
      spawned++
      if (spawned >= count) break
    }
  }

  private fun updateParticles(dt: Float) {
    for (pt in particles) {
      if (!pt.active) continue
      pt.life -= dt
      if (pt.life <= 0f) {
        pt.active = false
        continue
      }
      pt.x += pt.vx * dt
      pt.y += pt.vy * dt
      pt.z += pt.vz * dt
      pt.vy -= 14f * dt
    }
  }

  fun isPowerUpActive(type: PowerUpType): Boolean = (activePowerUpTimers[type] ?: 0f) > 0f

  fun getActivePowerUpUiList(): List<ActivePowerUpUi> =
    activePowerUpTimers.entries
      .filter { it.value > 0f }
      .sortedBy { it.key.ordinal }
      .map { ActivePowerUpUi(it.key, it.value, it.key.durationSeconds) }

  fun getCountdownLabel(): String =
    when {
      lastCountdownBucket >= 3 -> "3"
      lastCountdownBucket == 2 -> "2"
      lastCountdownBucket == 1 -> "1"
      else -> "RUN!"
    }

  private fun showBanner(text: String) {
    eventBanner = text
    eventBannerTimer = 2.0f
  }
}
