package com.example.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.engine.AtmosphereMode
import com.example.engine.GameScreen
import com.example.engine.GameSimulation
import com.example.engine.ObstacleInstance
import com.example.engine.ObstacleType
import com.example.engine.PowerUpType
import com.example.engine.SegmentTheme
import com.example.engine.TrackSegment
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class JungleRenderer(
  private val simulation: GameSimulation,
  private val onFrameTick: () -> Unit,
) : GLSurfaceView.Renderer {

  private var programId: Int = 0
  private var attrPos: Int = 0
  private var attrNorm: Int = 0
  private var uniMvp: Int = 0
  private var uniModel: Int = 0
  private var uniColor: Int = 0
  private var uniLightDir: Int = 0
  private var uniFogColor: Int = 0
  private var uniFogDensity: Int = 0
  private var uniEmissive: Int = 0
  private var uniAlpha: Int = 0

  private lateinit var cubeMesh: GlMesh
  private lateinit var cylMesh: GlMesh
  private lateinit var coneMesh: GlMesh
  private lateinit var sphereMesh: GlMesh
  private lateinit var discMesh: GlMesh

  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val vpMatrix = FloatArray(16)
  private val modelMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)

  private var aspectRatio: Float = 16f / 9f
  private var lastTimeNanos: Long = 0L
  private var uiSyncAccum: Float = 0f

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

    programId = buildShaderProgram()
    attrPos = GLES20.glGetAttribLocation(programId, "aPosition")
    attrNorm = GLES20.glGetAttribLocation(programId, "aNormal")
    uniMvp = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
    uniModel = GLES20.glGetUniformLocation(programId, "uModelMatrix")
    uniColor = GLES20.glGetUniformLocation(programId, "uColor")
    uniLightDir = GLES20.glGetUniformLocation(programId, "uLightDir")
    uniFogColor = GLES20.glGetUniformLocation(programId, "uFogColor")
    uniFogDensity = GLES20.glGetUniformLocation(programId, "uFogDensity")
    uniEmissive = GLES20.glGetUniformLocation(programId, "uEmissive")
    uniAlpha = GLES20.glGetUniformLocation(programId, "uAlpha")

    cubeMesh = MeshFactory.createUnitCube()
    cylMesh = MeshFactory.createCylinder(12)
    coneMesh = MeshFactory.createCone(10)
    sphereMesh = MeshFactory.createOctaSphere()
    discMesh = MeshFactory.createFlatDisc(14)

    lastTimeNanos = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
  }

  override fun onDrawFrame(gl: GL10?) {
    val now = System.nanoTime()
    val dt =
      if (lastTimeNanos == 0L) 0.016f
      else ((now - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
    lastTimeNanos = now

    simulation.update(dt)
    uiSyncAccum += dt
    if (uiSyncAccum >= 0.04f) {
      uiSyncAccum = 0f
      onFrameTick()
    }

    val isSunset = simulation.atmosphere == AtmosphereMode.SUNSET
    val fogR = if (isSunset) 0.78f else 0.48f
    val fogG = if (isSunset) 0.42f else 0.72f
    val fogB = if (isSunset) 0.34f else 0.64f

    GLES20.glClearColor(fogR, fogG, fogB, 1.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    GLES20.glUseProgram(programId)
    GLES20.glUniform3f(uniFogColor, fogR, fogG, fogB)
    GLES20.glUniform1f(uniFogDensity, if (isSunset) 0.0105f else 0.0088f)
    if (isSunset) {
      GLES20.glUniform3f(uniLightDir, -0.45f, 0.65f, 0.55f)
    } else {
      GLES20.glUniform3f(uniLightDir, 0.35f, 0.88f, 0.32f)
    }

    setupCamera()

    // Draw distant sun/glow sphere in sky
    drawSunOrb(isSunset)

    // Draw pooled environment segments
    for (seg in simulation.segments) {
      drawSegment(seg, isSunset)
    }

    // Draw pooled obstacles
    for (obs in simulation.obstacles) {
      if (obs.active) drawObstacle(obs)
    }

    // Draw pooled coins
    for (coin in simulation.coins) {
      if (coin.active) {
        // Contact shadow
        drawMesh(discMesh, coin.x, 0.02f, coin.z, 0.45f, 1f, 0.45f, 0f, 0f, 0f, 0.05f, 0.08f, 0.05f, 0f, 0.35f)
        // Spinning gold coin
        drawMesh(
          cylMesh,
          coin.x, coin.y, coin.z,
          0.62f, 0.14f, 0.62f,
          90f, coin.rotation * 57.3f, 0f,
          1.0f, 0.82f, 0.12f,
          0.35f, 1.0f,
        )
      }
    }

    // Draw pooled power-ups
    for (pu in simulation.powerUps) {
      if (pu.active) drawPowerUp(pu.type, pu.lane * GameSimulation.LANE_WIDTH, pu.bobPhase, pu.z, pu.rotation)
    }

    // Draw articulated explorer & shield aura (skip during menu diorama if desired, or show idling!)
    if (simulation.screen != GameScreen.MAIN_MENU &&
      simulation.screen != GameScreen.HOW_TO_PLAY &&
      simulation.screen != GameScreen.SETTINGS
    ) {
      drawExplorer()
    }

    // Draw articulated T-Rex
    drawTRex()

    // Draw particles
    for (pt in simulation.particles) {
      if (pt.active) {
        val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
        drawMesh(
          cubeMesh,
          pt.x, pt.y, pt.z,
          pt.size, pt.size, pt.size,
          pt.life * 180f, pt.life * 220f, 0f,
          pt.r, pt.g, pt.b,
          0.55f, alpha,
        )
      }
    }
  }

  private fun setupCamera() {
    val fov = 64f + simulation.cameraFovBoost * 11f
    Matrix.perspectiveM(projMatrix, 0, fov, aspectRatio, 0.3f, 220f)

    val shakeX =
      if (simulation.screenShake > 0.01f) sin(simulation.menuOrbitTime * 55f + simulation.runAnimPhase * 9f) * simulation.screenShake * 0.42f
      else 0f
    val shakeY =
      if (simulation.screenShake > 0.01f) cos(simulation.menuOrbitTime * 47f + simulation.runAnimPhase * 11f) * simulation.screenShake * 0.32f
      else 0f

    if (simulation.screen == GameScreen.MAIN_MENU ||
      simulation.screen == GameScreen.HOW_TO_PLAY ||
      simulation.screen == GameScreen.SETTINGS
    ) {
      // Dramatic temple diorama framing the roaring T-Rex
      val orbit = simulation.menuOrbitTime * 0.35f
      val eyeX = sin(orbit) * 3.8f
      val eyeZ = -2.2f + cos(orbit) * 1.4f
      Matrix.setLookAtM(
        viewMatrix, 0,
        eyeX, 3.3f, eyeZ,
        0f, 2.0f, simulation.trexZ,
        0f, 1f, 0f,
      )
    } else {
      val camX = simulation.playerX * 0.46f + shakeX
      val camY = 5.6f + simulation.playerY * 0.38f + shakeY
      val camZ = 10.6f
      val lookX = simulation.playerX * 0.60f
      val lookY = 1.2f + simulation.playerY * 0.25f
      Matrix.setLookAtM(
        viewMatrix, 0,
        camX, camY, camZ,
        lookX, lookY, -8.5f,
        0f, 1f, 0f,
      )
    }
    Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
  }

  private fun drawSunOrb(isSunset: Boolean) {
    if (isSunset) {
      drawMesh(sphereMesh, -28f, 32f, -155f, 18f, 18f, 18f, 0f, 0f, 0f, 1.0f, 0.56f, 0.22f, 0.95f, 1f)
    } else {
      drawMesh(sphereMesh, 22f, 44f, -155f, 13f, 13f, 13f, 0f, 0f, 0f, 1.0f, 0.96f, 0.78f, 0.95f, 1f)
    }
  }

  private fun drawSegment(seg: TrackSegment, isSunset: Boolean) {
    val zCenter = seg.zStart - GameSimulation.SEGMENT_LENGTH * 0.5f
    val len = GameSimulation.SEGMENT_LENGTH

    // Jungle flank grass/mud ground
    val grassR = if (isSunset) 0.16f else 0.14f
    val grassG = if (isSunset) 0.36f else 0.44f
    val grassB = if (isSunset) 0.15f else 0.18f
    drawMesh(cubeMesh, -12.5f, -0.55f, zCenter, 16f, 1.0f, len, 0f, 0f, 0f, grassR, grassG, grassB, 0f, 1f)
    drawMesh(cubeMesh, 12.5f, -0.55f, zCenter, 16f, 1.0f, len, 0f, 0f, 0f, grassR, grassG, grassB, 0f, 1f)

    // Water stream on stream segments (cuts under stone bridge!)
    if (seg.hasStream) {
      drawMesh(cubeMesh, 0f, -0.85f, zCenter, 46f, 0.4f, len * 0.78f, 0f, 0f, 0f, 0.12f, 0.56f, 0.74f, 0.25f, 0.9f)
      // Stone bridge deck & side parapets
      drawMesh(cubeMesh, 0f, -0.22f, zCenter, 8.6f, 0.44f, len, 0f, 0f, 0f, 0.50f, 0.48f, 0.44f, 0f, 1f)
      drawMesh(cubeMesh, -4.2f, 0.45f, zCenter, 0.45f, 0.9f, len, 0f, 0f, 0f, 0.45f, 0.43f, 0.39f, 0f, 1f)
      drawMesh(cubeMesh, 4.2f, 0.45f, zCenter, 0.45f, 0.9f, len, 0f, 0f, 0f, 0.45f, 0.43f, 0.39f, 0f, 1f)
    } else if (seg.theme == SegmentTheme.WOODEN_BRIDGE) {
      // Elevated wooden plank bridge over ravine stream
      drawMesh(cubeMesh, 0f, -1.2f, zCenter, 44f, 0.3f, len, 0f, 0f, 0f, 0.10f, 0.44f, 0.58f, 0.15f, 0.9f)
      for (p in 0 until 7) {
        val pz = seg.zStart - 1.0f - p * 2.0f
        drawMesh(cubeMesh, 0f, -0.14f, pz, 8.4f, 0.28f, 1.65f, 0f, 0f, 0f, 0.52f, 0.34f, 0.18f, 0f, 1f)
      }
      // Wooden side rails
      drawMesh(cubeMesh, -4.15f, 0.55f, zCenter, 0.25f, 0.25f, len, 0f, 0f, 0f, 0.42f, 0.26f, 0.13f, 0f, 1f)
      drawMesh(cubeMesh, 4.15f, 0.55f, zCenter, 0.25f, 0.25f, len, 0f, 0f, 0f, 0.42f, 0.26f, 0.13f, 0f, 1f)
    } else {
      // Main 3-lane track base (earthy stone/jungle trail)
      val pathR = if (seg.theme == SegmentTheme.TEMPLE_RUINS) 0.54f else 0.42f
      val pathG = if (seg.theme == SegmentTheme.TEMPLE_RUINS) 0.48f else 0.33f
      val pathB = if (seg.theme == SegmentTheme.TEMPLE_RUINS) 0.38f else 0.22f
      drawMesh(cubeMesh, 0f, -0.25f, zCenter, 8.6f, 0.5f, len, 0f, 0f, 0f, pathR, pathG, pathB, 0f, 1f)

      // Subtle lane border strips
      drawMesh(cubeMesh, -1.2f, 0.01f, zCenter, 0.12f, 0.02f, len, 0f, 0f, 0f, 0.29f, 0.23f, 0.16f, 0f, 0.7f)
      drawMesh(cubeMesh, 1.2f, 0.01f, zCenter, 0.12f, 0.02f, len, 0f, 0f, 0f, 0.29f, 0.23f, 0.16f, 0f, 0.7f)
    }

    // Giant prehistoric dinosaur footprints stamped along the dirt
    if (seg.theme == SegmentTheme.JUNGLE_TRAIL || seg.theme == SegmentTheme.CANYON_FOSSIL) {
      val fx = seg.footprintLane * GameSimulation.LANE_WIDTH
      val fz = zCenter + 2.5f
      drawMesh(discMesh, fx, 0.015f, fz, 0.85f, 1f, 1.1f, 0f, 0f, 0f, 0.22f, 0.17f, 0.11f, 0f, 0.65f)
      drawMesh(discMesh, fx - 0.22f, 0.016f, fz - 0.52f, 0.24f, 1f, 0.4f, 0f, 0f, 0f, 0.22f, 0.17f, 0.11f, 0f, 0.65f)
      drawMesh(discMesh, fx, 0.016f, fz - 0.58f, 0.24f, 1f, 0.45f, 0f, 0f, 0f, 0.22f, 0.17f, 0.11f, 0f, 0.65f)
      drawMesh(discMesh, fx + 0.22f, 0.016f, fz - 0.52f, 0.24f, 1f, 0.4f, 0f, 0f, 0f, 0.22f, 0.17f, 0.11f, 0f, 0.65f)
    }

    // Side jungle trees, bushes, fallen logs, rocky mountains, temple ruins & hanging vines
    drawJungleTree(-6.5f - seg.leftTreeVariant * 0.8f, zCenter + 3f, 1.0f + seg.leftTreeVariant * 0.18f)
    drawJungleTree(6.7f + seg.rightTreeVariant * 0.8f, zCenter - 3f, 1.1f + seg.rightTreeVariant * 0.15f)
    drawBush(-5.3f, zCenter - 3.5f, 1.1f)
    drawBush(5.4f, zCenter + 2.8f, 1.0f)

    // Distant rocky mountain peaks framing the horizon
    if (seg.index % 2 == 0) {
      drawMesh(coneMesh, -18f, 7.5f, zCenter, 13f, 16f, 13f, 0f, 18f, 0f, 0.28f, 0.32f, 0.34f, 0f, 1f)
      drawMesh(coneMesh, 19f, 8.5f, zCenter - 4f, 15f, 18f, 15f, 0f, -22f, 0f, 0.26f, 0.30f, 0.33f, 0f, 1f)
    }

    // Ancient temple ruin gateway + hanging vines
    if (seg.hasArchway) {
      drawMesh(cubeMesh, -4.9f, 3.2f, zCenter, 1.3f, 6.4f, 1.3f, 0f, 0f, 0f, 0.48f, 0.46f, 0.39f, 0f, 1f)
      drawMesh(cubeMesh, 4.9f, 3.2f, zCenter, 1.3f, 6.4f, 1.3f, 0f, 0f, 0f, 0.48f, 0.46f, 0.39f, 0f, 1f)
      drawMesh(cubeMesh, 0f, 6.5f, zCenter, 11.4f, 1.2f, 1.6f, 0f, 0f, 0f, 0.43f, 0.41f, 0.34f, 0f, 1f)
      // Moss & hanging vines on archway
      val sway = sin(simulation.menuOrbitTime * 2.2f + seg.vineOffset) * 7f
      drawMesh(cylMesh, -2.5f, 4.9f, zCenter, 0.12f, 2.2f, 0.12f, 0f, 0f, sway, 0.16f, 0.56f, 0.22f, 0f, 1f)
      drawMesh(cylMesh, 2.3f, 5.1f, zCenter, 0.12f, 1.8f, 0.12f, 0f, 0f, -sway, 0.16f, 0.56f, 0.22f, 0f, 1f)
    }
  }

  private fun drawJungleTree(x: Float, z: Float, scale: Float) {
    // Trunk
    drawMesh(cylMesh, x, 2.6f * scale, z, 0.72f * scale, 5.2f * scale, 0.72f * scale, 0f, 0f, 0f, 0.38f, 0.24f, 0.13f, 0f, 1f)
    // Layered canopy cones
    drawMesh(coneMesh, x, 5.4f * scale, z, 3.4f * scale, 2.8f * scale, 3.4f * scale, 0f, 0f, 0f, 0.10f, 0.44f, 0.18f, 0f, 1f)
    drawMesh(coneMesh, x, 7.0f * scale, z, 2.6f * scale, 2.5f * scale, 2.6f * scale, 0f, 25f, 0f, 0.14f, 0.54f, 0.22f, 0f, 1f)
  }

  private fun drawBush(x: Float, z: Float, s: Float) {
    drawMesh(sphereMesh, x, 0.55f * s, z, 1.35f * s, 1.05f * s, 1.35f * s, 0f, 0f, 0f, 0.13f, 0.48f, 0.20f, 0f, 1f)
    drawMesh(sphereMesh, x + 0.5f * s, 0.4f * s, z + 0.3f * s, 0.9f * s, 0.8f * s, 0.9f * s, 0f, 0f, 0f, 0.18f, 0.56f, 0.24f, 0f, 1f)
  }

  private fun drawObstacle(obs: ObstacleInstance) {
    val x = obs.lane * GameSimulation.LANE_WIDTH
    val z = obs.z

    when (obs.type) {
      ObstacleType.FALLEN_TREE -> {
        drawMesh(discMesh, x, 0.02f, z, 2.0f, 1f, 0.95f, 0f, 0f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0.45f)
        drawMesh(cylMesh, x, 0.52f, z, 0.86f, 2.1f, 0.86f, 0f, 0f, 90f, 0.40f, 0.24f, 0.13f, 0f, 1f)
        // Moss patches
        drawMesh(sphereMesh, x - 0.5f, 0.88f, z, 0.5f, 0.35f, 0.5f, 0f, 0f, 0f, 0.16f, 0.52f, 0.20f, 0f, 1f)
      }
      ObstacleType.LARGE_ROCK -> {
        drawMesh(discMesh, x, 0.02f, z, 2.0f, 1f, 1.8f, 0f, 0f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0.5f)
        drawMesh(sphereMesh, x, 1.05f, z, 1.85f, 2.15f, 1.65f, 12f, 22f, 0f, 0.44f, 0.43f, 0.41f, 0f, 1f)
        drawMesh(coneMesh, x + 0.35f, 1.65f, z - 0.1f, 0.9f, 1.2f, 0.9f, 10f, 0f, -15f, 0.36f, 0.36f, 0.35f, 0f, 1f)
      }
      ObstacleType.WOODEN_BARRIER -> {
        drawMesh(discMesh, x, 0.02f, z, 1.9f, 1f, 0.7f, 0f, 0f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0.4f)
        drawMesh(cubeMesh, x - 0.75f, 0.55f, z, 0.22f, 1.1f, 0.22f, 0f, 0f, 0f, 0.46f, 0.28f, 0.14f, 0f, 1f)
        drawMesh(cubeMesh, x + 0.75f, 0.55f, z, 0.22f, 1.1f, 0.22f, 0f, 0f, 0f, 0.46f, 0.28f, 0.14f, 0f, 1f)
        drawMesh(cubeMesh, x, 0.85f, z, 1.9f, 0.28f, 0.22f, 0f, 0f, 0f, 0.58f, 0.36f, 0.18f, 0f, 1f)
        drawMesh(cubeMesh, x, 0.42f, z, 1.8f, 0.22f, 0.18f, 0f, 0f, 0f, 0.52f, 0.32f, 0.16f, 0f, 1f)
      }
      ObstacleType.BROKEN_BRIDGE -> {
        // Dark chasm pit + jagged broken planks on edges
        drawMesh(cubeMesh, x, 0.03f, z, 2.0f, 0.08f, 1.9f, 0f, 0f, 0f, 0.04f, 0.05f, 0.06f, 0f, 1f)
        drawMesh(cubeMesh, x - 0.45f, 0.14f, z + 0.8f, 0.9f, 0.12f, 0.35f, 18f, 12f, -8f, 0.48f, 0.30f, 0.15f, 0f, 1f)
        drawMesh(cubeMesh, x + 0.42f, 0.14f, z - 0.8f, 0.9f, 0.12f, 0.35f, -15f, -10f, 8f, 0.48f, 0.30f, 0.15f, 0f, 1f)
      }
      ObstacleType.DEEP_HOLE -> {
        drawMesh(discMesh, x, 0.035f, z, 2.1f, 1f, 2.1f, 0f, 0f, 0f, 0.02f, 0.02f, 0.03f, 0f, 1f)
        // Warning rocky rim
        drawMesh(sphereMesh, x - 0.85f, 0.16f, z, 0.45f, 0.32f, 0.8f, 0f, 0f, 0f, 0.36f, 0.30f, 0.24f, 0f, 1f)
        drawMesh(sphereMesh, x + 0.85f, 0.16f, z, 0.45f, 0.32f, 0.8f, 0f, 0f, 0f, 0.36f, 0.30f, 0.24f, 0f, 1f)
      }
      ObstacleType.HANGING_BRANCH -> {
        // Side supports + overhead thick branch & hanging vine leaves (slide under!)
        drawMesh(cylMesh, x - 1.02f, 1.5f, z, 0.24f, 3.0f, 0.24f, 0f, 0f, 0f, 0.35f, 0.22f, 0.12f, 0f, 1f)
        drawMesh(cylMesh, x + 1.02f, 1.5f, z, 0.24f, 3.0f, 0.24f, 0f, 0f, 0f, 0.35f, 0.22f, 0.12f, 0f, 1f)
        drawMesh(cylMesh, x, 1.68f, z, 0.52f, 2.35f, 0.52f, 0f, 0f, 90f, 0.38f, 0.23f, 0.12f, 0f, 1f)
        drawMesh(sphereMesh, x - 0.4f, 2.05f, z, 1.2f, 0.9f, 0.9f, 0f, 0f, 0f, 0.14f, 0.56f, 0.20f, 0f, 1f)
        drawMesh(sphereMesh, x + 0.45f, 2.05f, z, 1.2f, 0.9f, 0.9f, 0f, 0f, 0f, 0.12f, 0.48f, 0.18f, 0f, 1f)
      }
      ObstacleType.STONE_PILLAR -> {
        drawMesh(discMesh, x, 0.02f, z, 1.8f, 1f, 1.8f, 0f, 0f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0.5f)
        drawMesh(cubeMesh, x, 0.25f, z, 1.45f, 0.5f, 1.45f, 0f, 0f, 0f, 0.48f, 0.46f, 0.40f, 0f, 1f)
        drawMesh(cubeMesh, x, 2.1f, z, 1.15f, 3.4f, 1.15f, 0f, 0f, 0f, 0.56f, 0.53f, 0.44f, 0f, 1f)
        drawMesh(cubeMesh, x, 3.85f, z, 1.45f, 0.4f, 1.45f, 0f, 0f, 0f, 0.45f, 0.43f, 0.37f, 0f, 1f)
      }
      ObstacleType.FIRE_PIT -> {
        drawMesh(discMesh, x, 0.03f, z, 1.9f, 1f, 1.6f, 0f, 0f, 0f, 0.18f, 0.10f, 0.08f, 0f, 1f)
        val flicker = 0.85f + 0.25f * abs(sin(simulation.menuOrbitTime * 15f + z))
        drawMesh(coneMesh, x - 0.35f, 0.55f * flicker, z, 0.55f, 1.15f * flicker, 0.55f, 0f, 0f, 0f, 1.0f, 0.36f, 0.08f, 0.85f, 1f)
        drawMesh(coneMesh, x + 0.32f, 0.50f * flicker, z + 0.12f, 0.5f, 1.0f * flicker, 0.5f, 0f, 0f, 0f, 1.0f, 0.45f, 0.10f, 0.85f, 1f)
        drawMesh(coneMesh, x, 0.68f * flicker, z - 0.08f, 0.62f, 1.35f * flicker, 0.62f, 0f, 0f, 0f, 1.0f, 0.82f, 0.20f, 0.95f, 1f)
      }
      ObstacleType.DINO_EGGS -> {
        drawMesh(discMesh, x, 0.03f, z, 1.9f, 1f, 1.6f, 0f, 0f, 0f, 0.34f, 0.24f, 0.14f, 0f, 0.9f)
        drawMesh(sphereMesh, x - 0.36f, 0.48f, z + 0.15f, 0.68f, 0.92f, 0.68f, 0f, 0f, 10f, 0.90f, 0.86f, 0.72f, 0f, 1f)
        drawMesh(sphereMesh, x + 0.36f, 0.48f, z + 0.10f, 0.66f, 0.90f, 0.66f, 0f, 0f, -12f, 0.86f, 0.82f, 0.68f, 0f, 1f)
        drawMesh(sphereMesh, x, 0.56f, z - 0.22f, 0.72f, 1.02f, 0.72f, -8f, 0f, 0f, 0.94f, 0.90f, 0.76f, 0f, 1f)
      }
      ObstacleType.COLLAPSING_PLATFORM -> {
        val dropY = -obs.collapseProgress * 1.1f
        val tilt = obs.collapseProgress * 22f
        drawMesh(cubeMesh, x, 0.12f + dropY, z, 1.95f, 0.28f, 1.85f, tilt, 0f, tilt * 0.4f, 0.52f, 0.44f, 0.38f, 0f, 1f)
        // Crack highlight
        drawMesh(cubeMesh, x, 0.27f + dropY, z, 1.6f, 0.04f, 0.12f, tilt, 25f, 0f, 0.14f, 0.10f, 0.08f, 0f, 1f)
      }
    }
  }

  private fun drawPowerUp(type: PowerUpType, x: Float, bobPhase: Float, z: Float, rotRad: Float) {
    val y = 1.1f + 0.22f * sin(bobPhase)
    val rotDeg = rotRad * 57.3f
    drawMesh(discMesh, x, 0.02f, z, 0.75f, 1f, 0.75f, 0f, 0f, 0f, 0.05f, 0.08f, 0.05f, 0f, 0.4f)

    when (type) {
      PowerUpType.MAGNET -> {
        drawMesh(cubeMesh, x - 0.22f, y, z, 0.22f, 0.7f, 0.22f, 0f, rotDeg, 0f, 0.95f, 0.22f, 0.22f, 0.45f, 1f)
        drawMesh(cubeMesh, x + 0.22f, y, z, 0.22f, 0.7f, 0.22f, 0f, rotDeg, 0f, 0.22f, 0.52f, 0.95f, 0.45f, 1f)
        drawMesh(cubeMesh, x, y - 0.26f, z, 0.66f, 0.22f, 0.22f, 0f, rotDeg, 0f, 0.92f, 0.78f, 0.20f, 0.45f, 1f)
      }
      PowerUpType.SHIELD -> {
        drawMesh(sphereMesh, x, y, z, 0.85f, 0.85f, 0.85f, 0f, rotDeg, 0f, 0.18f, 0.86f, 0.95f, 0.55f, 0.85f)
      }
      PowerUpType.SPEED_BOOST -> {
        drawMesh(coneMesh, x, y, z, 0.7f, 0.95f, 0.7f, 20f, rotDeg, 0f, 1.0f, 0.48f, 0.12f, 0.65f, 1f)
      }
      PowerUpType.DOUBLE_COINS -> {
        drawMesh(cylMesh, x, y, z, 0.85f, 0.2f, 0.85f, 90f, rotDeg, 0f, 1.0f, 0.86f, 0.15f, 0.65f, 1f)
        drawMesh(cubeMesh, x, y + 0.45f, z, 0.3f, 0.3f, 0.3f, 45f, rotDeg, 45f, 1.0f, 0.92f, 0.35f, 0.75f, 1f)
      }
    }
  }

  private fun drawExplorer() {
    val px = simulation.playerX
    val py = simulation.playerY
    val sliding = simulation.slideTimer > 0f
    val fallen = simulation.screen == GameScreen.GAME_OVER

    // Contact shadow
    val shadowScale = (1f - py * 0.22f).coerceIn(0.45f, 1f)
    drawMesh(discMesh, px, 0.02f, 0f, 0.95f * shadowScale, 1f, 0.95f * shadowScale, 0f, 0f, 0f, 0.04f, 0.04f, 0.04f, 0f, 0.45f)

    // Blink feedback while invulnerable
    if (simulation.invulnTimer > 0f && ((simulation.invulnTimer * 18f).toInt() % 2 == 0)) return

    val stride = sin(simulation.runAnimPhase) * 38f
    val leanDeg = simulation.laneLean * 45f

    val bodyTiltX =
      when {
        fallen -> 84f
        sliding -> -68f
        else -> 12f
      }
    val baseY =
      when {
        fallen -> 0.25f
        sliding -> py + 0.42f
        else -> py + 0.95f + abs(sin(simulation.runAnimPhase)) * 0.08f
      }

    // Torso (olive explorer jacket)
    drawMesh(cubeMesh, px, baseY, 0f, 0.52f, 0.66f, 0.30f, bodyTiltX, 0f, leanDeg, 0.78f, 0.64f, 0.38f, 0f, 1f)
    // Explorer backpack
    val packZ = if (sliding) -0.25f else 0.20f
    val packY = if (sliding) baseY - 0.1f else baseY + 0.04f
    drawMesh(cubeMesh, px, packY, packZ, 0.42f, 0.50f, 0.22f, bodyTiltX, 0f, leanDeg, 0.45f, 0.29f, 0.14f, 0f, 1f)

    // Head & safari hat
    val headY =
      when {
        fallen -> 0.32f
        sliding -> py + 0.62f
        else -> baseY + 0.54f
      }
    val headZ =
      when {
        fallen -> -0.65f
        sliding -> 0.32f
        else -> -0.05f
      }
    drawMesh(sphereMesh, px, headY, headZ, 0.36f, 0.38f, 0.36f, 0f, 0f, 0f, 0.92f, 0.74f, 0.58f, 0f, 1f)
    // Safari hat brim & crown
    drawMesh(cylMesh, px, headY + 0.18f, headZ, 0.58f, 0.05f, 0.58f, bodyTiltX * 0.3f, 0f, leanDeg, 0.56f, 0.40f, 0.20f, 0f, 1f)
    drawMesh(cylMesh, px, headY + 0.28f, headZ, 0.36f, 0.20f, 0.36f, bodyTiltX * 0.3f, 0f, leanDeg, 0.50f, 0.35f, 0.17f, 0f, 1f)

    if (!sliding && !fallen) {
      val legSwing = if (simulation.isJumping) 28f else stride
      // Left & Right Legs (khaki pants + dark boots)
      drawMesh(cubeMesh, px - 0.14f, py + 0.46f, 0f, 0.20f, 0.58f, 0.22f, legSwing, 0f, 0f, 0.32f, 0.36f, 0.26f, 0f, 1f)
      drawMesh(cubeMesh, px + 0.14f, py + 0.46f, 0f, 0.20f, 0.58f, 0.22f, -legSwing, 0f, 0f, 0.32f, 0.36f, 0.26f, 0f, 1f)
      // Left & Right Arms
      drawMesh(cubeMesh, px - 0.35f, baseY + 0.05f, 0f, 0.16f, 0.52f, 0.16f, -legSwing * 0.9f, 0f, -8f, 0.85f, 0.68f, 0.48f, 0f, 1f)
      drawMesh(cubeMesh, px + 0.35f, baseY + 0.05f, 0f, 0.16f, 0.52f, 0.16f, legSwing * 0.9f, 0f, 8f, 0.85f, 0.68f, 0.48f, 0f, 1f)
    } else if (sliding) {
      // Extended forward sliding legs
      drawMesh(cubeMesh, px - 0.13f, py + 0.20f, -0.42f, 0.20f, 0.22f, 0.68f, 0f, 0f, 0f, 0.32f, 0.36f, 0.26f, 0f, 1f)
      drawMesh(cubeMesh, px + 0.13f, py + 0.20f, -0.42f, 0.20f, 0.22f, 0.68f, 0f, 0f, 0f, 0.32f, 0.36f, 0.26f, 0f, 1f)
    }

    // Active Shield energy dome
    if (simulation.isPowerUpActive(PowerUpType.SHIELD)) {
      drawMesh(sphereMesh, px, py + 1.0f, 0f, 1.55f, 2.05f, 1.55f, 0f, simulation.runAnimPhase * 30f, 0f, 0.22f, 0.88f, 0.98f, 0.45f, 0.30f)
    }
    // Magnet golden ring aura
    if (simulation.isPowerUpActive(PowerUpType.MAGNET)) {
      drawMesh(cylMesh, px, py + 0.85f, 0f, 1.5f, 0.08f, 1.5f, 12f, simulation.runAnimPhase * 45f, 0f, 1.0f, 0.82f, 0.15f, 0.7f, 0.55f)
    }
  }

  private fun drawTRex() {
    val tx = simulation.trexX
    val tz = simulation.trexZ
    val bob = abs(sin(simulation.trexStridePhase)) * 0.22f
    val stride = sin(simulation.trexStridePhase) * 28f
    val lungeForward = simulation.trexAttackProgress * 0.65f
    val bodyPitch = 14f + simulation.trexAttackProgress * 22f

    // Massive contact shadow
    drawMesh(discMesh, tx, 0.025f, tz, 2.8f, 1f, 3.6f, 0f, 0f, 0f, 0.03f, 0.03f, 0.03f, 0f, 0.55f)

    val torsoY = 1.95f + bob - lungeForward * 0.35f
    val torsoZ = tz - lungeForward

    // Muscular Body & Belly
    drawMesh(cubeMesh, tx, torsoY, torsoZ, 1.45f, 1.50f, 2.15f, bodyPitch, 0f, 0f, 0.20f, 0.44f, 0.22f, 0f, 1f)
    drawMesh(cubeMesh, tx, torsoY - 0.32f, torsoZ - 0.15f, 1.15f, 0.85f, 1.90f, bodyPitch, 0f, 0f, 0.48f, 0.54f, 0.32f, 0f, 1f)

    // Dorsal spines along back
    for (s in 0 until 4) {
      drawMesh(
        coneMesh,
        tx, torsoY + 0.88f - s * 0.06f, torsoZ - 0.5f + s * 0.45f,
        0.24f, 0.45f, 0.32f,
        -15f, 0f, 0f,
        0.74f, 0.54f, 0.18f, 0f, 1f,
      )
    }

    // Thick Neck
    drawMesh(cubeMesh, tx, torsoY + 0.75f, torsoZ - 1.05f, 0.92f, 1.05f, 0.95f, -18f, 0f, 0f, 0.19f, 0.42f, 0.21f, 0f, 1f)

    // Upper Skull & Snout
    val headY = torsoY + 1.38f
    val headZ = torsoZ - 1.55f
    drawMesh(cubeMesh, tx, headY, headZ, 1.10f, 0.82f, 1.45f, 5f, 0f, 0f, 0.22f, 0.48f, 0.24f, 0f, 1f)
    // Glowing fierce amber eyes
    drawMesh(sphereMesh, tx - 0.54f, headY + 0.18f, headZ - 0.18f, 0.20f, 0.20f, 0.20f, 0f, 0f, 0f, 1.0f, 0.72f, 0.10f, 0.95f, 1f)
    drawMesh(sphereMesh, tx + 0.54f, headY + 0.18f, headZ - 0.18f, 0.20f, 0.20f, 0.20f, 0f, 0f, 0f, 1.0f, 0.72f, 0.10f, 0.95f, 1f)

    // Upper Teeth
    for (t in -1..1) {
      drawMesh(coneMesh, tx + t * 0.28f, headY - 0.45f, headZ - 0.55f, 0.12f, 0.26f, 0.12f, 180f, 0f, 0f, 0.96f, 0.95f, 0.88f, 0.1f, 1f)
    }

    // Articulated Lower Jaw (opens wide when roaring!)
    val jawAngle = 8f + simulation.trexMouthOpen * 38f
    drawMesh(
      cubeMesh,
      tx, headY - 0.42f - simulation.trexMouthOpen * 0.28f, headZ - 0.08f,
      0.92f, 0.34f, 1.28f,
      jawAngle, 0f, 0f,
      0.24f, 0.46f, 0.24f, 0f, 1f,
    )

    // Tiny T-Rex Arms
    drawMesh(cubeMesh, tx - 0.72f, torsoY - 0.15f, torsoZ - 0.72f, 0.22f, 0.52f, 0.24f, -45f, 0f, 0f, 0.22f, 0.46f, 0.24f, 0f, 1f)
    drawMesh(cubeMesh, tx + 0.72f, torsoY - 0.15f, torsoZ - 0.72f, 0.22f, 0.52f, 0.24f, -45f, 0f, 0f, 0.22f, 0.46f, 0.24f, 0f, 1f)

    // Muscular Stomping Legs
    drawMesh(cubeMesh, tx - 0.56f, 0.95f + max(0f, sin(simulation.trexStridePhase)) * 0.25f, torsoZ + 0.15f, 0.48f, 1.65f, 0.58f, stride, 0f, 0f, 0.18f, 0.38f, 0.19f, 0f, 1f)
    drawMesh(cubeMesh, tx + 0.56f, 0.95f + max(0f, -sin(simulation.trexStridePhase)) * 0.25f, torsoZ + 0.15f, 0.48f, 1.65f, 0.58f, -stride, 0f, 0f, 0.18f, 0.38f, 0.19f, 0f, 1f)

    // Segmented Swaying Tail
    val tailSway = sin(simulation.trexStridePhase * 0.6f) * 14f
    drawMesh(cubeMesh, tx + tailSway * 0.02f, torsoY - 0.1f, torsoZ + 1.45f, 0.95f, 0.90f, 1.4f, -8f, tailSway, 0f, 0.19f, 0.42f, 0.21f, 0f, 1f)
    drawMesh(coneMesh, tx + tailSway * 0.05f, torsoY - 0.15f, torsoZ + 2.65f, 0.72f, 1.85f, 0.72f, 82f, tailSway * 1.4f, 0f, 0.17f, 0.38f, 0.19f, 0f, 1f)
  }

  private fun drawMesh(
    mesh: GlMesh,
    tx: Float, ty: Float, tz: Float,
    sx: Float, sy: Float, sz: Float,
    rx: Float, ry: Float, rz: Float,
    r: Float, g: Float, b: Float,
    emissive: Float,
    alpha: Float,
  ) {
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, tx, ty, tz)
    if (ry != 0f) Matrix.rotateM(modelMatrix, 0, ry, 0f, 1f, 0f)
    if (rx != 0f) Matrix.rotateM(modelMatrix, 0, rx, 1f, 0f, 0f)
    if (rz != 0f) Matrix.rotateM(modelMatrix, 0, rz, 0f, 0f, 1f)
    Matrix.scaleM(modelMatrix, 0, sx, sy, sz)

    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

    GLES20.glUniformMatrix4fv(uniMvp, 1, false, mvpMatrix, 0)
    GLES20.glUniformMatrix4fv(uniModel, 1, false, modelMatrix, 0)
    GLES20.glUniform3f(uniColor, r, g, b)
    GLES20.glUniform1f(uniEmissive, emissive)
    GLES20.glUniform1f(uniAlpha, alpha)

    mesh.bindAndDraw(attrPos, attrNorm)
  }

  fun dispose() {
    if (::cubeMesh.isInitialized) cubeMesh.dispose()
    if (::cylMesh.isInitialized) cylMesh.dispose()
    if (::coneMesh.isInitialized) coneMesh.dispose()
    if (::sphereMesh.isInitialized) sphereMesh.dispose()
    if (::discMesh.isInitialized) discMesh.dispose()
    if (programId != 0) {
      GLES20.glDeleteProgram(programId)
      programId = 0
    }
  }

  private fun buildShaderProgram(): Int {
    val vsCode =
      """
      uniform mat4 uMvpMatrix;
      uniform mat4 uModelMatrix;
      attribute vec4 aPosition;
      attribute vec3 aNormal;
      varying vec3 vWorldNormal;
      varying float vDist;
      void main() {
        vec4 clipPos = uMvpMatrix * aPosition;
        gl_Position = clipPos;
        vWorldNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
        vDist = length(clipPos.xyz);
      }
      """
        .trimIndent()

    val fsCode =
      """
      precision mediump float;
      uniform vec3 uColor;
      uniform vec3 uLightDir;
      uniform vec3 uFogColor;
      uniform float uFogDensity;
      uniform float uEmissive;
      uniform float uAlpha;
      varying vec3 vWorldNormal;
      varying float vDist;
      void main() {
        vec3 n = normalize(vWorldNormal);
        float diff = max(dot(n, normalize(uLightDir)), 0.0);
        float hemi = 0.42 + 0.16 * n.y;
        vec3 lit = uColor * (hemi + diff * 0.68) + uColor * uEmissive;
        float fogFactor = exp(-pow(vDist * uFogDensity, 2.0));
        fogFactor = clamp(fogFactor, 0.0, 1.0);
        vec3 finalColor = mix(uFogColor, lit, fogFactor);
        gl_FragColor = vec4(finalColor, uAlpha);
      }
      """
        .trimIndent()

    val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsCode)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsCode)
    val prog = GLES20.glCreateProgram()
    GLES20.glAttachShader(prog, vs)
    GLES20.glAttachShader(prog, fs)
    GLES20.glLinkProgram(prog)
    GLES20.glDeleteShader(vs)
    GLES20.glDeleteShader(fs)
    return prog
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    return shader
  }
}
