package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.example.engine.SoundEffect
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundEngine(private val context: Context) {
  private var soundPool: SoundPool? = null
  private val soundIds = mutableMapOf<SoundEffect, Int>()
  @Volatile private var soundEnabled: Boolean = true
  @Volatile private var released: Boolean = false
  @Volatile private var ambienceActive: Boolean = false
  @Volatile private var initialized: Boolean = false

  private var ambienceThread: Thread? = null
  private var ambienceTrack: AudioTrack? = null

  fun init() {
    if (initialized || released) return
    initialized = true
    thread(name = "DinoAudioInit", isDaemon = true) {
      try {
        val attrs =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        soundPool = pool

        val dir = File(context.cacheDir, "dino_sfx").apply { mkdirs() }
        val sampleRate = 22050

        loadBuffer(pool, dir, SoundEffect.BUTTON_CLICK, synthClick(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.COIN, synthCoin(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.JUMP, synthJump(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.SLIDE, synthSlide(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.COLLISION, synthCollision(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.DINO_ROAR, synthRoar(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.POWER_UP, synthPowerUp(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.FOOTSTEP, synthFootstep(sampleRate), sampleRate)
        loadBuffer(pool, dir, SoundEffect.GAME_OVER, synthGameOver(sampleRate), sampleRate)

        startAmbienceLoop()
      } catch (_: Throwable) {
        // Gracefully degrade if audio subsystem is unavailable (e.g. headless tests)
      }
    }
  }

  private fun loadBuffer(
    pool: SoundPool,
    dir: File,
    effect: SoundEffect,
    pcm: ShortArray,
    sampleRate: Int,
  ) {
    if (released) return
    val wavFile = File(dir, "${effect.name.lowercase()}.wav")
    writeWav(wavFile, pcm, sampleRate)
    val id = pool.load(wavFile.absolutePath, 1)
    soundIds[effect] = id
  }

  fun play(effect: SoundEffect) {
    if (!soundEnabled || released) return
    val pool = soundPool ?: return
    val id = soundIds[effect] ?: return
    val vol =
      when (effect) {
        SoundEffect.FOOTSTEP -> 0.25f
        SoundEffect.BUTTON_CLICK -> 0.45f
        SoundEffect.COIN -> 0.55f
        SoundEffect.DINO_ROAR -> 0.85f
        else -> 0.70f
      }
    try {
      pool.play(id, vol, vol, 1, 0, 1.0f)
    } catch (_: Throwable) {}
  }

  fun setEnabled(enabled: Boolean) {
    soundEnabled = enabled
    if (!enabled) {
      try {
        soundPool?.autoPause()
      } catch (_: Throwable) {}
    }
  }

  fun setAmbiencePlaying(playing: Boolean) {
    ambienceActive = playing
  }

  fun release() {
    released = true
    ambienceActive = false
    try {
      ambienceTrack?.stop()
      ambienceTrack?.release()
    } catch (_: Throwable) {}
    ambienceTrack = null
    try {
      soundPool?.release()
    } catch (_: Throwable) {}
    soundPool = null
  }

  private fun startAmbienceLoop() {
    val sampleRate = 22050
    val seconds = 3
    val count = sampleRate * seconds
    val pcm = ShortArray(count)
    val rng = Random(42)
    var lp = 0f
    for (i in 0 until count) {
      val t = i.toFloat() / sampleRate
      // Gentle jungle canopy breeze + subtle tropical pulse
      val noise = rng.nextFloat() * 2f - 1f
      lp = lp * 0.96f + noise * 0.04f
      val windEnv = 0.55f + 0.45f * sin(2f * PI.toFloat() * t / seconds)
      val cricket =
        if ((i % 8000) < 900) sin(2f * PI.toFloat() * 3400f * t) * 0.015f else 0f
      val sample = (lp * 0.35f * windEnv + cricket).coerceIn(-1f, 1f)
      pcm[i] = (sample * 11000f).toInt().toShort()
    }

    ambienceThread =
      thread(name = "DinoAmbience", isDaemon = true) {
        try {
          val bufSize =
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
              )
              .coerceAtLeast(4096)
          val track =
            AudioTrack.Builder()
              .setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_GAME)
                  .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                  .build()
              )
              .setAudioFormat(
                AudioFormat.Builder()
                  .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                  .setSampleRate(sampleRate)
                  .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                  .build()
              )
              .setBufferSizeInBytes(bufSize)
              .setTransferMode(AudioTrack.MODE_STREAM)
              .build()
          ambienceTrack = track
          track.play()

          val silent = ShortArray(2048)
          var offset = 0
          val chunk = 2048
          while (!released) {
            if (soundEnabled && ambienceActive) {
              val len = minOf(chunk, pcm.size - offset)
              track.write(pcm, offset, len)
              offset = (offset + len) % pcm.size
            } else {
              track.write(silent, 0, silent.size)
              Thread.sleep(60)
            }
          }
        } catch (_: Throwable) {}
      }
  }

  private fun synthClick(sr: Int): ShortArray {
    val n = (sr * 0.06f).toInt()
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val env = exp(-t * 55f)
      val s = sin(2f * PI.toFloat() * (780f - t * 2400f) * t) * env
      (s * 16000f).toInt().toShort()
    }
  }

  private fun synthCoin(sr: Int): ShortArray {
    val n = (sr * 0.22f).toInt()
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val freq = if (t < 0.065f) 988f else 1318.5f
      val env = exp(-t * 14f)
      val s = (sin(2f * PI.toFloat() * freq * t) + 0.35f * sin(4f * PI.toFloat() * freq * t)) * env
      (s.coerceIn(-1f, 1f) * 18000f).toInt().toShort()
    }
  }

  private fun synthJump(sr: Int): ShortArray {
    val n = (sr * 0.18f).toInt()
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val freq = 240f + t * 1800f
      val env = exp(-t * 13f)
      val s = sin(2f * PI.toFloat() * freq * t) * env
      (s * 18000f).toInt().toShort()
    }
  }

  private fun synthSlide(sr: Int): ShortArray {
    val n = (sr * 0.24f).toInt()
    val rng = Random(7)
    var lp = 0f
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val noise = rng.nextFloat() * 2f - 1f
      lp = lp * 0.72f + noise * 0.28f
      val env = sin((t / 0.24f) * PI.toFloat()) * exp(-t * 4f)
      ((lp + 0.25f * sin(2f * PI.toFloat() * (320f - t * 650f) * t)) * env * 18000f)
        .toInt()
        .toShort()
    }
  }

  private fun synthCollision(sr: Int): ShortArray {
    val n = (sr * 0.28f).toInt()
    val rng = Random(13)
    var lp = 0f
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val noise = rng.nextFloat() * 2f - 1f
      lp = lp * 0.85f + noise * 0.15f
      val thud = sin(2f * PI.toFloat() * (135f * exp(-t * 10f)) * t)
      val env = exp(-t * 12f)
      ((thud * 0.75f + lp * 0.65f) * env * 24000f).toInt().coerceIn(-32767, 32767).toShort()
    }
  }

  private fun synthRoar(sr: Int): ShortArray {
    val n = (sr * 0.95f).toInt()
    val rng = Random(99)
    var lp = 0f
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val noise = rng.nextFloat() * 2f - 1f
      lp = lp * 0.88f + noise * 0.12f
      val env =
        (if (t < 0.12f) t / 0.12f else 1f) * exp(-(t - 0.12f).coerceAtLeast(0f) * 2.4f)
      val baseFreq = 92f - 28f * (t / 0.95f)
      val growl =
        sin(2f * PI.toFloat() * baseFreq * t) +
          0.6f * sin(2f * PI.toFloat() * baseFreq * 1.52f * t) +
          0.4f * sin(2f * PI.toFloat() * baseFreq * 2.31f * t)
      val mod = 0.7f + 0.3f * sin(2f * PI.toFloat() * 26f * t)
      ((growl * 0.38f * mod + lp * 0.65f) * env * 22000f)
        .toInt()
        .coerceIn(-32767, 32767)
        .toShort()
    }
  }

  private fun synthPowerUp(sr: Int): ShortArray {
    val n = (sr * 0.42f).toInt()
    val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val idx = ((t / 0.42f) * 4f).toInt().coerceIn(0, 3)
      val f = notes[idx]
      val env = exp(-t * 5.2f)
      val s = (sin(2f * PI.toFloat() * f * t) + 0.3f * sin(4f * PI.toFloat() * f * t)) * env
      (s.coerceIn(-1f, 1f) * 18000f).toInt().toShort()
    }
  }

  private fun synthFootstep(sr: Int): ShortArray {
    val n = (sr * 0.055f).toInt()
    val rng = Random(21)
    var lp = 0f
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val noise = rng.nextFloat() * 2f - 1f
      lp = lp * 0.80f + noise * 0.20f
      val env = exp(-t * 58f)
      val s = (lp * 0.7f + 0.3f * sin(2f * PI.toFloat() * 110f * t)) * env
      (s * 16000f).toInt().toShort()
    }
  }

  private fun synthGameOver(sr: Int): ShortArray {
    val n = (sr * 0.75f).toInt()
    val notes = floatArrayOf(392f, 349.23f, 293.66f, 220f)
    return ShortArray(n) { i ->
      val t = i.toFloat() / sr
      val idx = ((t / 0.75f) * 4f).toInt().coerceIn(0, 3)
      val f = notes[idx]
      val env = exp(-t * 3.0f)
      val s = sin(2f * PI.toFloat() * f * t) * env
      (s * 20000f).toInt().toShort()
    }
  }

  private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
    val dataBytes = pcm.size * 2
    val totalLen = 36 + dataBytes
    val header = ByteArray(44)
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    writeIntLE(header, 4, totalLen)
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    writeIntLE(header, 16, 16)
    writeShortLE(header, 20, 1)
    writeShortLE(header, 22, 1)
    writeIntLE(header, 24, sampleRate)
    writeIntLE(header, 28, sampleRate * 2)
    writeShortLE(header, 32, 2)
    writeShortLE(header, 34, 16)
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    writeIntLE(header, 40, dataBytes)

    FileOutputStream(file).use { out ->
      out.write(header)
      val buf = ByteArray(dataBytes)
      for (i in pcm.indices) {
        val v = pcm[i].toInt()
        buf[i * 2] = (v and 0xff).toByte()
        buf[i * 2 + 1] = ((v ushr 8) and 0xff).toByte()
      }
      out.write(buf)
    }
  }

  private fun writeIntLE(b: ByteArray, offset: Int, v: Int) {
    b[offset] = (v and 0xff).toByte()
    b[offset + 1] = ((v ushr 8) and 0xff).toByte()
    b[offset + 2] = ((v ushr 16) and 0xff).toByte()
    b[offset + 3] = ((v ushr 24) and 0xff).toByte()
  }

  private fun writeShortLE(b: ByteArray, offset: Int, v: Int) {
    b[offset] = (v and 0xff).toByte()
    b[offset + 1] = ((v ushr 8) and 0xff).toByte()
  }
}
