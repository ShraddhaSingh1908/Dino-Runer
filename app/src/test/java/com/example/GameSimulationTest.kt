package com.example

import com.example.engine.GameScreen
import com.example.engine.GameSimulation
import com.example.engine.InputCommand
import com.example.engine.ObstacleType
import com.example.engine.PowerUpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSimulationTest {

  private fun advance(sim: GameSimulation, seconds: Float, step: Float = 0.02f) {
    var t = 0f
    while (t < seconds) {
      sim.update(step)
      t += step
    }
  }

  @Test
  fun `countdown transitions cleanly to playing`() {
    val sim = GameSimulation()
    sim.startNewRunCountdown()
    assertEquals(GameScreen.COUNTDOWN, sim.screen)
    assertEquals("3", sim.getCountdownLabel())

    advance(sim, 3.4f)
    assertEquals(GameScreen.PLAYING, sim.screen)
  }

  @Test
  fun `lane switching respects 3-lane bounds`() {
    val sim = GameSimulation()
    sim.startNewRunCountdown()
    advance(sim, 3.4f)

    sim.enqueueInput(InputCommand.MOVE_LEFT)
    sim.update(0.016f)
    assertEquals(-1, sim.targetLane)

    sim.enqueueInput(InputCommand.MOVE_LEFT)
    sim.update(0.016f)
    assertEquals(-1, sim.targetLane)

    sim.enqueueInput(InputCommand.MOVE_RIGHT)
    sim.enqueueInput(InputCommand.MOVE_RIGHT)
    sim.update(0.016f)
    assertEquals(1, sim.targetLane)
  }

  @Test
  fun `jump and slide mechanics update player physics`() {
    val sim = GameSimulation()
    sim.startNewRunCountdown()
    advance(sim, 3.4f)

    sim.enqueueInput(InputCommand.JUMP)
    sim.update(0.04f)
    assertTrue(sim.isJumping)
    assertTrue(sim.playerY > 0f)

    // Fast-fall slide mid-air
    sim.enqueueInput(InputCommand.SLIDE)
    advance(sim, 0.6f)
    assertFalse(sim.isJumping)
    assertTrue(sim.slideTimer > 0f)
  }

  @Test
  fun `coin collection and double coins powerup increment score`() {
    val sim = GameSimulation()
    sim.startNewRunCountdown()
    advance(sim, 3.4f)

    sim.coins[0].apply {
      active = true
      x = 0f
      y = 0.85f
      z = -0.2f
    }
    sim.update(0.03f)
    assertEquals(1, sim.runCoins)

    sim.powerUps[0].apply {
      active = true
      type = PowerUpType.DOUBLE_COINS
      lane = 0
      z = -0.2f
    }
    sim.update(0.03f)
    assertTrue(sim.isPowerUpActive(PowerUpType.DOUBLE_COINS))

    sim.coins[1].apply {
      active = true
      x = 0f
      y = 0.85f
      z = -0.2f
    }
    sim.update(0.03f)
    assertEquals(3, sim.runCoins)
  }

  @Test
  fun `shield absorbs obstacle collision and stumble triggers T-Rex close chase`() {
    var ended = false
    val sim = GameSimulation(onRunEnded = { _, _ -> ended = true })
    sim.startNewRunCountdown()
    advance(sim, 3.4f)

    // Activate shield
    sim.powerUps[0].apply {
      active = true
      type = PowerUpType.SHIELD
      lane = 0
      z = -0.1f
    }
    sim.update(0.03f)
    assertTrue(sim.isPowerUpActive(PowerUpType.SHIELD))

    // Hit stone pillar with shield -> shield absorbs, game continues
    sim.obstacles[0].apply {
      active = true
      type = ObstacleType.STONE_PILLAR
      lane = 0
      z = -0.1f
      hitProcessed = false
    }
    sim.update(0.03f)
    assertEquals(GameScreen.PLAYING, sim.screen)
    assertFalse(sim.isPowerUpActive(PowerUpType.SHIELD))

    // Wait out invulnerability
    advance(sim, 1.4f)

    // First minor hit -> stumble level 1
    sim.obstacles[1].apply {
      active = true
      type = ObstacleType.FALLEN_TREE
      lane = 0
      z = -0.1f
      hitProcessed = false
    }
    sim.update(0.03f)
    assertEquals(GameScreen.PLAYING, sim.screen)
    assertEquals(1, sim.stumbleLevel)

    // Wait out invulnerability, hit second minor obstacle -> caught by T-Rex!
    advance(sim, 1.2f)
    sim.obstacles[2].apply {
      active = true
      type = ObstacleType.WOODEN_BARRIER
      lane = 0
      z = -0.1f
      hitProcessed = false
    }
    sim.update(0.03f)
    assertEquals(GameScreen.GAME_OVER, sim.screen)
    assertTrue(ended)
  }

  @Test
  fun `pause freezes simulation and restart resets progress`() {
    val sim = GameSimulation()
    sim.startNewRunCountdown()
    advance(sim, 3.4f)
    advance(sim, 1.0f)
    val distBeforePause = sim.distanceFloat
    assertTrue(distBeforePause > 10f)

    sim.pauseRun()
    assertEquals(GameScreen.PAUSED, sim.screen)
    advance(sim, 1.0f)
    assertEquals(distBeforePause, sim.distanceFloat, 0.001f)

    sim.resumeRun()
    assertEquals(GameScreen.PLAYING, sim.screen)

    sim.startNewRunCountdown()
    assertEquals(0f, sim.distanceFloat, 0.001f)
    assertEquals(0, sim.runCoins)
    assertEquals(0, sim.stumbleLevel)
  }

  @Test
  fun `procedural waves never create impossible 3-lane wall`() {
    val sim = GameSimulation()
    sim.startNewRunCountdown()
    advance(sim, 3.4f)
    // Run long distance with invulnerability to inspect spawned waves
    for (step in 0 until 1500) {
      sim.invulnTimer = 1f
      sim.update(0.04f)
      val activeByZ =
        sim.obstacles.filter { it.active }.groupBy { (it.z / 2.5f).toInt() }
      for ((_, group) in activeByZ) {
        val blockedLanes =
          group.filter { it.type.requiresLaneSwitch }.map { it.lane }.toSet()
        assertTrue(
          "All 3 lanes cannot be blocked by unjumpable/unslideable obstacles simultaneously",
          blockedLanes.size < 3,
        )
      }
    }
  }
}
