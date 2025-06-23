package com.example.hapticwallpaper // Kendi paket adınızı buraya yazın

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val setWallpaperButton: Button = findViewById(R.id.setWallpaperButton)
        setWallpaperButton.setOnClickListener {
            // Canlı duvar kağıdı seçme ekranını açan Intent
            // Bu intent, telefondaki tüm canlı duvar kağıtlarını listeler.
            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
            startActivity(intent)
        }
    }
}