package com.example.hapticwallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt
import kotlin.random.Random

class BubblesWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return BubblesEngine()
    }

    data class Bubble(
        var x: Float,
        var y: Float,
        var radius: Float,
        var xVel: Float,
        var yVel: Float,
        var life: Float,
        val maxLife: Float
    )

    data class ExplosionParticle(
        var x: Float,
        var y: Float,
        var xVel: Float,
        var yVel: Float,
        var life: Float,
        val maxLife: Float,
        var alpha: Int
    )

    inner class BubblesEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val bubbles = CopyOnWriteArrayList<Bubble>()
        private val explosionParticles = CopyOnWriteArrayList<ExplosionParticle>()
        private var visible = true
        private val drawRunner = Runnable { drawFrame() }

        private var bubbleBitmap: Bitmap? = null
        private var backgroundBitmap: Bitmap? = null

        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val destRect = RectF()
        private val explosionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var screenWidth = 0
        private var screenHeight = 0

        private val FRAME_DELAY_MS = 16L
        private val MAX_BUBBLES = 8
        private val BUBBLE_MAX_LIFE = 800f
        private val PARTICLES_PER_EXPLOSION = 40


        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            holder.setFormat(PixelFormat.TRANSLUCENT)
        }


        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenWidth = width
            screenHeight = height

            if (width > 0 && height > 0) {
                if (bubbleBitmap == null) {
                    val originalBubble = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.bubble_texture)
                    bubbleBitmap = originalBubble
                }
                val originalBg = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.underwater_background)
                backgroundBitmap = Bitmap.createScaledBitmap(originalBg, width, height, true)
                if (originalBg != backgroundBitmap) {
                    originalBg.recycle()
                }
            }
            handler.post(drawRunner)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) handler.post(drawRunner) else handler.removeCallbacks(drawRunner)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (visible && event.action == MotionEvent.ACTION_DOWN) {
                if (bubbles.size < MAX_BUBBLES) {
                    addBubble(event.x, event.y)
                }
            }
        }

        private fun addBubble(x: Float, y: Float) {
            val radius = 180f
            val xVel = (Random.nextFloat() - 0.5f) * 6f
            val yVel = (Random.nextFloat() - 0.5f) * 6f
            bubbles.add(Bubble(x, y, radius, xVel, yVel, BUBBLE_MAX_LIFE, BUBBLE_MAX_LIFE))
        }

        private fun createExplosion(x: Float, y: Float) {
            val explosionLife = 50f
            repeat(PARTICLES_PER_EXPLOSION) {
                val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
                val speed = Random.nextFloat() * 12f + 3f
                explosionParticles.add(
                    ExplosionParticle(x, y,
                        kotlin.math.cos(angle) * speed,
                        kotlin.math.sin(angle) * speed,
                        explosionLife, explosionLife, 255)
                )
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null && bubbleBitmap != null && backgroundBitmap != null) {
                    canvas.drawBitmap(backgroundBitmap!!, 0f, 0f, null)

                    bubbles.forEach { b ->
                        b.life--
                        b.xVel *= 0.999f
                        b.yVel *= 0.999f
                        b.x += b.xVel
                        b.y += b.yVel
                    }

                    val bubblesToRemove = mutableListOf<Bubble>()
                    // --- DEĞİŞİKLİK: Hassasiyet %80'e çekildi ---
                    val collisionPaddingFactor = 0.8f

                    for (i in 0 until bubbles.size) {
                        for (j in i + 1 until bubbles.size) {
                            val b1 = bubbles[i]
                            val b2 = bubbles[j]
                            val dx = b2.x - b1.x
                            val dy = b2.y - b1.y
                            val distance = sqrt(dx * dx + dy * dy)
                            if (distance < (b1.radius + b2.radius) * collisionPaddingFactor) {
                                bubblesToRemove.add(b1)
                                bubblesToRemove.add(b2)
                            }
                        }
                    }

                    bubbles.forEach { b ->
                        val effectiveRadius = b.radius * collisionPaddingFactor
                        if (b.x - effectiveRadius < 0 || b.x + effectiveRadius > screenWidth || b.y - effectiveRadius < 0 || b.y + effectiveRadius > screenHeight || b.life <= 0) {
                            bubblesToRemove.add(b)
                        }
                    }

                    bubbles.forEach { b ->
                        if (!bubblesToRemove.contains(b)) {
                            val lifeRatio = (b.life / b.maxLife).coerceIn(0f, 1f)
                            destRect.set(b.x - b.radius, b.y - b.radius, b.x + b.radius, b.y + b.radius)
                            bitmapPaint.alpha = (255 * lifeRatio).toInt()
                            canvas.drawBitmap(bubbleBitmap!!, null, destRect, bitmapPaint)
                        }
                    }

                    explosionParticles.forEach { p ->
                        p.life--
                        p.x += p.xVel; p.y += p.yVel; p.xVel *= 0.97f; p.yVel *= 0.97f
                        p.alpha = (255 * (p.life / p.maxLife)).toInt()
                        if (p.alpha <= 0) { explosionParticles.remove(p) }
                        else {
                            explosionPaint.color = Color.WHITE
                            explosionPaint.alpha = p.alpha
                            canvas.drawCircle(p.x, p.y, 4f, explosionPaint)
                        }
                    }

                    bubblesToRemove.distinct().forEach { b ->
                        createExplosion(b.x, b.y)
                        bubbles.remove(b)
                    }
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            handler.removeCallbacks(drawRunner)
            if (visible) handler.postDelayed(drawRunner, FRAME_DELAY_MS)
        }
    }
}