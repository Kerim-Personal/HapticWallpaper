package com.example.hapticwallpaper

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SkeletonWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = SkeletonEngine()

    data class Point(var x: Float, var y: Float)

    data class BodyPart(
        val name: String, // Hata ayıklama için parçaya isim verdik
        val bitmap: Bitmap,
        val pivot: Point,
        var angle: Float,
        val restingAngle: Float
    ) {
        var angularVelocity = 0f
        private var angularAcceleration = 0f

        // Bu parçanın dünyadaki nihai pozisyonu
        val worldPos = Point(0f, 0f)

        fun updatePhysics() {
            angularVelocity += angularAcceleration
            angle += angularVelocity
            angularVelocity *= 0.95f
            val gravity = 0.04f
            angularAcceleration = -gravity * (angle - restingAngle)
        }
    }

    inner class SkeletonEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = Runnable { drawFrame() }
        private var visible = true
        private val paint = Paint()

        private var screenWidth = 0
        private var screenHeight = 0

        // Parça listesi
        private val parts = mutableListOf<BodyPart>()

        // Bağlanma noktalarını bir haritada tutacağız
        private val attachments = mutableMapOf<BodyPart, Point>()

        private var grabbedPart: BodyPart? = null

        private fun initializeSkeleton() {
            parts.clear()
            attachments.clear()

            // GÖRSELLERİ GÜVENLİ BİR ŞEKİLDE YÜKLEME
            // ÖNEMLİ: Dosya adlarınızın buradakilerle aynı olduğundan emin olun!
            val torsoBmp = BitmapFactory.decodeResource(resources, R.drawable.torso)
            val headBmp = BitmapFactory.decodeResource(resources, R.drawable.head)
            val armLBmp = BitmapFactory.decodeResource(resources, R.drawable.arm_l)
            val armRBmp = BitmapFactory.decodeResource(resources, R.drawable.arm_r)
            val legLBmp = BitmapFactory.decodeResource(resources, R.drawable.leg_l)
            val legRBmp = BitmapFactory.decodeResource(resources, R.drawable.leg_r)

            // Eğer görsellerden herhangi biri yüklenemezse, hata ver ve devam etme.
            if (listOf(torsoBmp, headBmp, armLBmp, armRBmp, legLBmp, legRBmp).any { it == null }) {
                Log.e("Skeleton", "Bir veya daha fazla iskelet parçası resmi 'res/drawable' klasöründe bulunamadı!")
                return
            }

            // Kendi resimlerinize göre PIVOT ve BAĞLANMA noktalarını ayarlayın!
            val leftShoulderAttach = Point(20f, 50f)
            val rightShoulderAttach = Point(torsoBmp.width - 20f, 50f)
            val leftHipAttach = Point(60f, torsoBmp.height - 40f)
            val rightHipAttach = Point(torsoBmp.width - 60f, torsoBmp.height - 40f)
            val headAttachPoint = Point(torsoBmp.width / 2f, 25f)

            // Parçaları Oluştur
            val torso = BodyPart("torso", torsoBmp, Point(torsoBmp.width / 2f, torsoBmp.height / 2f), 0f, 0f)
            val head = BodyPart("head", headBmp, Point(headBmp.width / 2f, headBmp.height.toFloat() - 20f), 0f, 0f)
            val leftArm = BodyPart("leftArm", armLBmp, Point(armLBmp.width - 20f, 20f), 2.8f, 2.8f)
            val rightArm = BodyPart("rightArm", armRBmp, Point(20f, 20f), -2.8f, -2.8f)
            val leftLeg = BodyPart("leftLeg", legLBmp, Point(legLBmp.width / 2f, 20f), 1.7f, 1.7f)
            val rightLeg = BodyPart("rightLeg", legRBmp, Point(legRBmp.width / 2f, 20f), -1.7f, -1.7f)

            // Parçaları listeye ekle
            parts.addAll(listOf(torso, head, leftArm, rightArm, leftLeg, rightLeg))

            // Bağlanma noktalarını ayarla
            attachments[head] = headAttachPoint
            attachments[leftArm] = leftShoulderAttach
            attachments[rightArm] = rightShoulderAttach
            attachments[leftLeg] = leftHipAttach
            attachments[rightLeg] = rightHipAttach
        }

        // Bu fonksiyon, bir parçayı ebeveynine (gövdeye) göre çizer.
        private fun drawPart(canvas: Canvas, part: BodyPart, torsoMatrix: Matrix) {
            val attachPoint = attachments[part] ?: return

            val worldAttachPoint = floatArrayOf(attachPoint.x, attachPoint.y)
            torsoMatrix.mapPoints(worldAttachPoint)

            canvas.save()
            canvas.translate(worldAttachPoint[0], worldAttachPoint[1])
            canvas.rotate(part.angle * (180f / Math.PI.toFloat()))
            canvas.translate(-part.pivot.x, -part.pivot.y)
            canvas.drawBitmap(part.bitmap, 0f, 0f, paint)
            canvas.restore()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas == null) return

                val torso = parts.firstOrNull { it.name == "torso" }
                // Eğer gövde (torso) bulunamazsa hiçbir şey çizme
                if (torso == null) {
                    canvas.drawColor(Color.RED) // Hata olduğunu belli etmek için ekranı kırmızı yap
                    Log.e("Skeleton", "Çizim hatası: Gövde (torso) bulunamadı!")
                    return
                }

                canvas.drawColor(Color.BLACK)

                // Fizik güncellemeleri
                parts.filter { it.name != "torso" }.forEach { it.updatePhysics() }

                // Gövdeyi merkeze yerleştir
                val torsoX = (screenWidth - torso.bitmap.width) / 2f
                val torsoY = (screenHeight - torso.bitmap.height) / 3f
                val torsoMatrix = Matrix()
                torsoMatrix.setTranslate(torsoX, torsoY)
                canvas.drawBitmap(torso.bitmap, torsoMatrix, paint)

                // Diğer parçaları gövdeye göre çiz
                parts.filter { it.name != "torso" }.forEach { part ->
                    drawPart(canvas, part, torsoMatrix)
                }

            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
            handler.removeCallbacks(drawRunner)
            if (visible) handler.postDelayed(drawRunner, 16)
        }

        // --- Diğer Service Metotları ---
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenWidth = width; screenHeight = height
            initializeSkeleton()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) handler.post(drawRunner) else handler.removeCallbacks(drawRunner)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            this.visible = false
            handler.removeCallbacks(drawRunner)
            parts.forEach { it.bitmap.recycle() } // Belleği temizle
        }

        override fun onTouchEvent(event: MotionEvent) {
            // Dokunma metodu eklendi
            val touchX = event.x
            val touchY = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Dokunmaya en yakın parçayı bul (gövde ve kafa hariç)
                    grabbedPart = parts.filter { it.name != "torso" && it.name != "head" }.minByOrNull {
                        val torso = parts.first { t -> t.name == "torso" }
                        val attachPoint = attachments[it] ?: Point(0f, 0f)
                        val worldAttachPoint = floatArrayOf(attachPoint.x, attachPoint.y)

                        val torsoX = (screenWidth - torso.bitmap.width) / 2f
                        val torsoY = (screenHeight - torso.bitmap.height) / 3f
                        val torsoMatrix = Matrix()
                        torsoMatrix.setTranslate(torsoX, torsoY)
                        torsoMatrix.mapPoints(worldAttachPoint)

                        sqrt((worldAttachPoint[0] - touchX).pow(2) + (worldAttachPoint[1] - touchY).pow(2))
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    grabbedPart?.let { part ->
                        val torso = parts.first { t -> t.name == "torso" }
                        val attachPoint = attachments[part] ?: Point(0f, 0f)
                        val worldAttachPoint = floatArrayOf(attachPoint.x, attachPoint.y)

                        val torsoX = (screenWidth - torso.bitmap.width) / 2f
                        val torsoY = (screenHeight - torso.bitmap.height) / 3f
                        val torsoMatrix = Matrix()
                        torsoMatrix.setTranslate(torsoX, torsoY)
                        torsoMatrix.mapPoints(worldAttachPoint)

                        val newAngle = atan2(touchY - worldAttachPoint[1], touchX - worldAttachPoint[0])
                        part.angle = newAngle
                    }
                }
                MotionEvent.ACTION_UP -> {
                    grabbedPart = null
                }
            }
        }
    }
}