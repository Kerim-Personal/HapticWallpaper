package com.example.hapticwallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class FlameWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return FlameEngine()
    }


    data class FlameParticle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var alpha: Float,
        var speedY: Float,
        var speedX: Float,
        var life: Float,
        val maxLife: Float,
        val color: Int
    )

    inner class FlameEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val particles = CopyOnWriteArrayList<FlameParticle>()

        private var isTouching = false
        private var touchX = 0f
        private var touchY = 0f
        private var visible = true

        private val drawRunner = Runnable { drawFrame() }


        private val FRAME_DELAY_MS = 16L

        private val PARTICLES_PER_TICK = 15

        private val PARTICLE_MAX_LIFE = 60f

        private val PARTICLE_INITIAL_RADIUS = 35f

        private val BLUR_RADIUS = 25f


        private val flameColors = listOf(
            Color.rgb(255, 255, 220),
            Color.rgb(255, 200, 100),
            Color.rgb(255, 150, 0),
            Color.rgb(200, 50, 0)
        )

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            handler.post(drawRunner)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            this.visible = false
            handler.removeCallbacks(drawRunner)
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (visible) {
                touchX = event.x
                touchY = event.y
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> isTouching = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isTouching = false
                }
            }
        }

        private fun addFlameParticle(x: Float, y: Float) {
            val life = Random.nextFloat() * PARTICLE_MAX_LIFE * 0.5f + PARTICLE_MAX_LIFE * 0.5f
            val radius = PARTICLE_INITIAL_RADIUS + Random.nextFloat() * 10f
            val speedY = Random.nextFloat() * 4f + 4f
            val speedX = (Random.nextFloat() - 0.5f) * 2f
            val color = flameColors[Random.nextInt(1, flameColors.size)]
            particles.add(FlameParticle(x, y, radius, 1f, speedY, speedX, life, life, color))
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {

                    canvas.drawColor(Color.BLACK)

                    if (isTouching) {
                        for (i in 0 until PARTICLES_PER_TICK) {

                            val spreadX = (Random.nextFloat() - 0.5f) * 30f
                            val spreadY = (Random.nextFloat() - 0.5f) * 30f
                            addFlameParticle(touchX + spreadX, touchY + spreadY)
                        }
                    }


                    paint.maskFilter = BlurMaskFilter(BLUR_RADIUS, BlurMaskFilter.Blur.NORMAL)


                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)


                    val particlesToRemove = mutableListOf<FlameParticle>()
                    for (p in particles) {
                        p.life--
                        if (p.life <= 0) {
                            particlesToRemove.add(p)
                            continue
                        }


                        p.y -= p.speedY
                        p.x += p.speedX + (Random.nextFloat() - 0.5f) * 2.5f
                        p.radius *= 0.96f

                        val lifeRatio = p.life / p.maxLife
                        p.alpha = lifeRatio


                        paint.color = p.color
                        paint.alpha = (255 * p.alpha).toInt().coerceIn(0, 255)


                        if (p.radius > 1) {
                            canvas.drawCircle(p.x, p.y, p.radius, paint)
                        }
                    }
                    particles.removeAll(particlesToRemove)

                }
            } finally {

                paint.xfermode = null
                paint.maskFilter = null

                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            handler.removeCallbacks(drawRunner)
            if (visible) {
                handler.postDelayed(drawRunner, FRAME_DELAY_MS)
            }
        }
    }
}