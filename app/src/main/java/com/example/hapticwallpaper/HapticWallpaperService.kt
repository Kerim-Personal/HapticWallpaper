package com.example.hapticwallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.random.Random

// Canlı duvar kağıdımızın ana hizmet sınıfı
class HapticWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return HapticWallpaperEngine()
    }

    // Dokunulduğunda oluşturulacak görsel parçacıklar için veri sınıfı
    data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float, // Aynı zamanda çarpışma yarıçapı
        var startColor: Int,
        var endColor: Int,
        var alpha: Int,
        val creationTime: Long,
        var speedX: Float, // X eksenindeki hız
        var speedY: Float,  // Y eksenindeki hız
        var mass: Float, // Çarpışma hesaplamaları için kütle (basitlik için şimdilik aynı)
        var isAlive: Boolean = true // Parçacığın hala aktif olup olmadığını kontrol etmek için
    )

    // Patlayan topların ekranda bıraktığı renk lekeleri için veri sınıfı
    data class Splatter(
        val x: Float,
        val y: Float,
        val color: Int,
        var alpha: Int,
        var initialRadius: Float, // Başlangıç yarıçapı
        var currentRadius: Float, // Mevcut yarıçap
        val finalRadius: Float,   // Ulaşacağı son yarıçap
        val creationTime: Long
    )

    // Canlı duvar kağıdının çizim ve etkileşim mantığını içeren iç sınıf (Engine)
    inner class HapticWallpaperEngine : Engine() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val particles = CopyOnWriteArrayList<Particle>()
        private val splatters = CopyOnWriteArrayList<Splatter>()
        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = Runnable { drawFrame() }

        // ------------- Efekt Ayarları -------------

        private val PARTICLE_LIFETIME = 1500L // Parçacık ömrü (1.5 saniye)
        private val SPLATTER_LIFETIME = 1000L // Leke ömrünü kısalttık (1 saniye - daha hızlı kaybolsun)

        private val INITIAL_RADIUS_MIN = 30f // Başlangıç daire yarıçapı minimum (büyüttük)
        private val INITIAL_RADIUS_MAX = 50f // Başlangıç daire yarıçapı maksimum (büyüttük)
        private val INITIAL_MASS = 1f // Parçacık kütlesi

        // Leke boyutları için yeni çarpanlar
        private val SPLATTER_INITIAL_RADIUS_FACTOR = 1.0f // Parçacık yarıçapı kadar başlasın
        private val SPLATTER_FINAL_RADIUS_FACTOR = 3.0f // Kendi yarıçapının 3 katına çıksın

        private val MAX_PARTICLES_ON_DOWN = 15 // İlk dokunuşta fırlatılacak parçacık sayısı (artırdık)
        private val MAX_PARTICLES_ON_MOVE = 8 // Basılı tutulduğunda/hareket ederken fırlatılacak parçacık sayısı (artırdık)
        private val MAX_PARTICLES_TOTAL = 200 // Toplam maksimum parçacık (artırdık)
        private val MAX_SPLATTERS_TOTAL = 150 // Toplam maksimum leke (artırdık)

        private val PARTICLE_SPEED_MULTIPLIER = 10f // Parçacık fırlatma hızını ayarlayan çarpan (artırdık)
        private val MIN_INITIAL_SPEED = 5f // Minimum başlangıç hızı (artırdık)

        private val MIN_TIME_BETWEEN_MOVE_PARTICLES = 50L // Hareket ederken yeni parçacık eklemek için minimum süre (kısalttık)

        private val FRAME_DELAY_MS = 16L // Çizim döngüsü için periyodik güncelleme hızı (yaklaşık 60 FPS)

        // ------------- Engine Yaşam Döngüsü Metotları -------------

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            handler.post(drawRunner)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            particles.clear()
            splatters.clear()
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            handler.removeCallbacks(drawRunner)
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    for (i in 0 until MAX_PARTICLES_ON_DOWN) {
                        addParticle(event.x, event.y)
                    }
                    drawFrame()
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentTime = System.currentTimeMillis()
                    val lastParticleTime = particles.lastOrNull()?.creationTime ?: 0L
                    if (currentTime - lastParticleTime > MIN_TIME_BETWEEN_MOVE_PARTICLES) {
                        for (i in 0 until MAX_PARTICLES_ON_MOVE) {
                            addParticle(event.x, event.y)
                        }
                    }
                    drawFrame()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    drawFrame()
                }
            }
        }

        // ------------- Yardımcı Metotlar -------------

        private fun addParticle(x: Float, y: Float) {
            while (particles.size >= MAX_PARTICLES_TOTAL) {
                particles.removeAt(0)
            }

            val startColor = getRandomColor()
            val endColor = Color.argb(0, Color.red(startColor), Color.green(startColor), Color.blue(startColor))

            val radius = Random.nextFloat() * (INITIAL_RADIUS_MAX - INITIAL_RADIUS_MIN) + INITIAL_RADIUS_MIN

            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * PARTICLE_SPEED_MULTIPLIER + MIN_INITIAL_SPEED
            val speedX = cos(angle) * speed
            val speedY = sin(angle) * speed

            particles.add(Particle(x, y, radius, startColor, endColor, 255, System.currentTimeMillis(), speedX, speedY, INITIAL_MASS))
        }

        private fun getRandomColor(): Int {
            // Canlı ve parlak renkler için daha sınırlı aralıklar deneyelim
            return Color.argb(
                255,
                Random.nextInt(100, 256), // Daha az koyu kırmızı
                Random.nextInt(100, 256), // Daha az koyu yeşil
                Random.nextInt(100, 256)  // Daha az koyu mavi
            )
        }

        private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
            val inverseFraction = 1 - fraction
            val a = (Color.alpha(color1) * inverseFraction + Color.alpha(color2) * fraction).toInt()
            val r = (Color.red(color1) * inverseFraction + Color.red(color2) * fraction).toInt()
            val g = (Color.green(color1) * inverseFraction + Color.green(color2) * fraction).toInt()
            val b = (Color.blue(color1) * inverseFraction + Color.blue(color2) * fraction).toInt()
            return Color.argb(a, r, g, b)
        }

        // Bir parçacık ömrünü tamamladığında veya çarpıştığında leke oluşturur
        private fun createSplatter(x: Float, y: Float, color: Int, initialParticleRadius: Float) {
            while (splatters.size >= MAX_SPLATTERS_TOTAL) {
                splatters.removeAt(0) // En eski lekeleri kaldır
            }

            val initialSplatterRadius = initialParticleRadius * SPLATTER_INITIAL_RADIUS_FACTOR
            val finalSplatterRadius = initialParticleRadius * SPLATTER_FINAL_RADIUS_FACTOR

            // Lekeyi başlangıçta daha opak yapalım
            splatters.add(Splatter(x, y, color, 255, initialSplatterRadius, initialSplatterRadius, finalSplatterRadius, System.currentTimeMillis()))
        }


        // Duvar kağıdını çizen ana döngü metodu
        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK) // Arka planı siyahla temizle

                    val currentTime = System.currentTimeMillis()

                    // Lekeleri güncelle ve çiz (parçacıklardan önce çizilmeli)
                    val splattersToRemove = mutableListOf<Splatter>()
                    for (splatter in splatters) {
                        val elapsedTime = currentTime - splatter.creationTime
                        if (elapsedTime >= SPLATTER_LIFETIME) {
                            splattersToRemove.add(splatter)
                            continue
                        }

                        val progress = elapsedTime.toFloat() / SPLATTER_LIFETIME

                        // Lekenin şeffaflığını ve boyutunu güncelle
                        splatter.alpha = (255 * (1 - progress)).toInt().coerceIn(0, 255) // Hızlı şeffaflaşma
                        // Radius'u başlangıçtan sona doğru lineer olarak büyüt
                        splatter.currentRadius = splatter.initialRadius + (splatter.finalRadius - splatter.initialRadius) * progress

                        paint.color = splatter.color
                        paint.alpha = splatter.alpha
                        canvas.drawCircle(splatter.x, splatter.y, splatter.currentRadius, paint)
                    }
                    splatters.removeAll(splattersToRemove)


                    // Parçacıkları güncelle, çarpışmaları kontrol et ve çiz
                    val particlesToRemove = mutableListOf<Particle>()
                    updateParticles() // Konum ve çarpışma güncellemeleri

                    for (particle in particles) {
                        // isAlive bayrağı false ise bu parçacığı atla
                        if (!particle.isAlive) {
                            particlesToRemove.add(particle)
                            continue
                        }

                        val elapsedTime = currentTime - particle.creationTime
                        if (elapsedTime >= PARTICLE_LIFETIME) {
                            // Parçacık ömrünü tamamladığında leke oluştur ve kaldır
                            createSplatter(particle.x, particle.y, particle.startColor, particle.radius)
                            particle.isAlive = false // Öldü olarak işaretle
                            particlesToRemove.add(particle) // Kaldırılacaklar listesine ekle
                            continue
                        }

                        val progress = elapsedTime.toFloat() / PARTICLE_LIFETIME

                        // Yarıçapı ve şeffaflığı güncelle
                        particle.radius = particle.radius * (1 - progress * 0.7f) // Daha hızlı küçülme
                        particle.alpha = (255 * (1 - progress)).toInt().coerceIn(0, 255)

                        val currentColor = interpolateColor(particle.startColor, particle.endColor, progress)

                        paint.color = currentColor
                        paint.alpha = particle.alpha

                        canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
                    }

                    particles.removeAll(particlesToRemove)
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            if (isVisible) {
                handler.removeCallbacks(drawRunner)
                handler.postDelayed(drawRunner, FRAME_DELAY_MS)
            }
        }

        // Tüm parçacıkları günceller ve çarpışmaları kontrol eder
        private fun updateParticles() {
            // Parçacıkların konumlarını güncelle
            for (particle in particles) {
                particle.x += particle.speedX
                particle.y += particle.speedY

                // Ekran kenarlarından yansıma (basit duvar çarpışması)
                if (particle.x - particle.radius < 0 || particle.x + particle.radius > surfaceHolder.surfaceFrame.width()) {
                    particle.speedX *= -1 // Hızı ters çevir
                    // Kenara sıkışmayı önlemek için hafifçe içeri it
                    if (particle.x - particle.radius < 0) particle.x = particle.radius
                    if (particle.x + particle.radius > surfaceHolder.surfaceFrame.width()) particle.x = surfaceHolder.surfaceFrame.width() - particle.radius
                }
                if (particle.y - particle.radius < 0 || particle.y + particle.radius > surfaceHolder.surfaceFrame.height()) {
                    particle.speedY *= -1 // Hızı ters çevir
                    // Kenara sıkışmayı önlemek için hafifçe içeri it
                    if (particle.y - particle.radius < 0) particle.y = particle.radius
                    if (particle.y + particle.radius > surfaceHolder.surfaceFrame.height()) particle.y = surfaceHolder.surfaceFrame.height() - particle.radius
                }
            }

            // Tüm parçacık çiftleri arasındaki çarpışmaları kontrol et
            for (i in 0 until particles.size) {
                // Sadece yaşayan parçacıkları kontrol et
                if (!particles[i].isAlive) continue

                for (j in i + 1 until particles.size) {
                    // Sadece yaşayan parçacıkları kontrol et
                    if (!particles[j].isAlive) continue

                    val p1 = particles[i]
                    val p2 = particles[j]

                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y
                    val distance = sqrt(dx * dx + dy * dy)

                    // Çarpışma algılandı mı?
                    if (distance < p1.radius + p2.radius) {
                        // Çarpışma çözme
                        resolveCollision(p1, p2)

                        // **Burada patlama/leke oluşturma!**
                        // Çarpışan her iki parçacık için de leke oluşturalım
                        createSplatter(p1.x, p1.y, p1.startColor, p1.radius)
                        createSplatter(p2.x, p2.y, p2.startColor, p2.radius)

                        // İsterseniz çarpışan parçacıkları "öldürebilir" ve hemen kaldırabilirsiniz
                        // p1.isAlive = false
                        // p2.isAlive = false
                        // Ancak şu anki mantıkta sadece leke bırakıp yola devam ediyorlar, bu daha dinamik.
                    }
                }
            }
        }

        // İki parçacık arasındaki çarpışmayı çözer (basit elastik çarpışma)
        private fun resolveCollision(p1: Particle, p2: Particle) {
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val distance = sqrt(dx * dx + dy * dy)

            // Çakışmayı önlemek için parçacıkları hafifçe ayır
            val overlap = (p1.radius + p2.radius) - distance
            if (overlap > 0) {
                val adjustX = dx / distance * overlap / 2
                val adjustY = dy / distance * overlap / 2
                p1.x -= adjustX
                p1.y -= adjustY
                p2.x += adjustX
                p2.y += adjustY
            }

            // Çarpışma normali boyunca hız bileşenlerini al
            val normalX = dx / distance
            val normalY = dy / distance
            val tangentX = -normalY
            val tangentY = normalX

            val dp1n = p1.speedX * normalX + p1.speedY * normalY
            val dp1t = p1.speedX * tangentX + p1.speedY * tangentY
            val dp2n = p2.speedX * normalX + p2.speedY * normalY
            val dp2t = p2.speedX * tangentX + p2.speedY * tangentY

            // Hızları çarpışma normali boyunca değiştir (kütleleri eşit varsayımıyla)
            val newDp1n = dp2n
            val newDp2n = dp1n

            // Yeni hız vektörlerini oluştur
            p1.speedX = newDp1n * normalX + dp1t * tangentX
            p1.speedY = newDp1n * normalY + dp1t * tangentY
            p2.speedX = newDp2n * normalX + dp2t * tangentX
            p2.speedY = newDp2n * normalY + dp2t * tangentY
        }
    }
}