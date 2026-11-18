package com.example.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable OpenGL VBO mesh holding interleaved (px, py, pz, nx, ny, nz) floats.
 */
class GlMesh(vertices: FloatArray) {
  val vertexCount: Int = vertices.size / 6
  private var vboId: Int = 0

  init {
    val ids = IntArray(1)
    GLES20.glGenBuffers(1, ids, 0)
    vboId = ids[0]
    val fb: FloatBuffer =
      ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    fb.put(vertices)
    fb.position(0)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, fb, GLES20.GL_STATIC_DRAW)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
  }

  fun bindAndDraw(posAttr: Int, normAttr: Int) {
    if (vboId == 0) return
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
    val stride = 6 * 4
    GLES20.glEnableVertexAttribArray(posAttr)
    GLES20.glVertexAttribPointer(posAttr, 3, GLES20.GL_FLOAT, false, stride, 0)
    GLES20.glEnableVertexAttribArray(normAttr)
    GLES20.glVertexAttribPointer(normAttr, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
  }

  fun dispose() {
    if (vboId != 0) {
      val ids = intArrayOf(vboId)
      GLES20.glDeleteBuffers(1, ids, 0)
      vboId = 0
    }
  }
}

object MeshFactory {

  fun createUnitCube(): GlMesh {
    val verts = ArrayList<Float>(36 * 6)
    fun addQuad(
      x0: Float, y0: Float, z0: Float,
      x1: Float, y1: Float, z1: Float,
      x2: Float, y2: Float, z2: Float,
      x3: Float, y3: Float, z3: Float,
      nx: Float, ny: Float, nz: Float,
    ) {
      addTri(verts, x0, y0, z0, x1, y1, z1, x2, y2, z2, nx, ny, nz)
      addTri(verts, x0, y0, z0, x2, y2, z2, x3, y3, z3, nx, ny, nz)
    }
    val h = 0.5f
    // Front (+Z)
    addQuad(-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0f, 0f, 1f)
    // Back (-Z)
    addQuad(h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0f, 0f, -1f)
    // Left (-X)
    addQuad(-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1f, 0f, 0f)
    // Right (+X)
    addQuad(h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1f, 0f, 0f)
    // Top (+Y)
    addQuad(-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0f, 1f, 0f)
    // Bottom (-Y)
    addQuad(-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0f, -1f, 0f)
    return GlMesh(verts.toFloatArray())
  }

  fun createCylinder(segments: Int = 12): GlMesh {
    val verts = ArrayList<Float>(segments * 12 * 6)
    val r = 0.5f
    for (i in 0 until segments) {
      val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
      val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
      val c0 = cos(a0)
      val s0 = sin(a0)
      val c1 = cos(a1)
      val s1 = sin(a1)
      val x0 = c0 * r
      val z0 = s0 * r
      val x1 = c1 * r
      val z1 = s1 * r

      // Side
      addTri(verts, x0, -0.5f, z0, x1, -0.5f, z1, x1, 0.5f, z1, c0, 0f, s0)
      addTri(verts, x0, -0.5f, z0, x1, 0.5f, z1, x0, 0.5f, z0, c0, 0f, s0)
      // Top cap
      addTri(verts, 0f, 0.5f, 0f, x0, 0.5f, z0, x1, 0.5f, z1, 0f, 1f, 0f)
      // Bottom cap
      addTri(verts, 0f, -0.5f, 0f, x1, -0.5f, z1, x0, -0.5f, z0, 0f, -1f, 0f)
    }
    return GlMesh(verts.toFloatArray())
  }

  fun createCone(segments: Int = 12): GlMesh {
    val verts = ArrayList<Float>(segments * 6 * 6)
    val r = 0.5f
    for (i in 0 until segments) {
      val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
      val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
      val c0 = cos(a0)
      val s0 = sin(a0)
      val c1 = cos(a1)
      val s1 = sin(a1)
      val x0 = c0 * r
      val z0 = s0 * r
      val x1 = c1 * r
      val z1 = s1 * r
      val nx = (c0 + c1) * 0.45f
      val nz = (s0 + s1) * 0.45f
      addTri(verts, x0, -0.5f, z0, x1, -0.5f, z1, 0f, 0.5f, 0f, nx, 0.45f, nz)
      addTri(verts, 0f, -0.5f, 0f, x1, -0.5f, z1, x0, -0.5f, z0, 0f, -1f, 0f)
    }
    return GlMesh(verts.toFloatArray())
  }

  fun createOctaSphere(): GlMesh {
    val verts = ArrayList<Float>()
    val r = 0.5f
    val rings = 8
    val sectors = 12
    for (i in 0 until rings) {
      val phi0 = (i.toFloat() / rings) * PI.toFloat() - PI.toFloat() / 2f
      val phi1 = ((i + 1).toFloat() / rings) * PI.toFloat() - PI.toFloat() / 2f
      for (j in 0 until sectors) {
        val th0 = (j.toFloat() / sectors) * 2f * PI.toFloat()
        val th1 = ((j + 1).toFloat() / sectors) * 2f * PI.toFloat()

        val p00x = cos(phi0) * cos(th0)
        val p00y = sin(phi0)
        val p00z = cos(phi0) * sin(th0)

        val p10x = cos(phi1) * cos(th0)
        val p10y = sin(phi1)
        val p10z = cos(phi1) * sin(th0)

        val p11x = cos(phi1) * cos(th1)
        val p11y = sin(phi1)
        val p11z = cos(phi1) * sin(th1)

        val p01x = cos(phi0) * cos(th1)
        val p01y = sin(phi0)
        val p01z = cos(phi0) * sin(th1)

        addTri(
          verts,
          p00x * r, p00y * r, p00z * r,
          p10x * r, p10y * r, p10z * r,
          p11x * r, p11y * r, p11z * r,
          p00x, p00y, p00z,
        )
        addTri(
          verts,
          p00x * r, p00y * r, p00z * r,
          p11x * r, p11y * r, p11z * r,
          p01x * r, p01y * r, p01z * r,
          p00x, p00y, p00z,
        )
      }
    }
    return GlMesh(verts.toFloatArray())
  }

  fun createFlatDisc(segments: Int = 16): GlMesh {
    val verts = ArrayList<Float>(segments * 3 * 6)
    val r = 0.5f
    for (i in 0 until segments) {
      val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
      val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
      addTri(
        verts,
        0f, 0f, 0f,
        cos(a1) * r, 0f, sin(a1) * r,
        cos(a0) * r, 0f, sin(a0) * r,
        0f, 1f, 0f,
      )
    }
    return GlMesh(verts.toFloatArray())
  }

  private fun addTri(
    out: MutableList<Float>,
    x0: Float, y0: Float, z0: Float,
    x1: Float, y1: Float, z1: Float,
    x2: Float, y2: Float, z2: Float,
    nx: Float, ny: Float, nz: Float,
  ) {
    out.add(x0); out.add(y0); out.add(z0); out.add(nx); out.add(ny); out.add(nz)
    out.add(x1); out.add(y1); out.add(z1); out.add(nx); out.add(ny); out.add(nz)
    out.add(x2); out.add(y2); out.add(z2); out.add(nx); out.add(ny); out.add(nz)
  }
}
