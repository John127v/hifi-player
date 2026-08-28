package com.example.hifiplayer

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private var viz: Visualizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()

        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(permission)
        }

        setContent {
            val ctx = LocalContext.current
            var songs by remember { mutableStateOf(listOf<File>()) }
            var idx by remember { mutableStateOf(-1) }
            var isPlay by remember { mutableStateOf(false) }
            var pos by remember { mutableStateOf(0L) }
            var dur by remember { mutableStateOf(0L) }
            var fft by remember { mutableStateOf(List(50) { 0.08f }) }
            var vuL by remember { mutableStateOf(0.1f) }
            var vuR by remember { mutableStateOf(0.1f) }
            var volume by remember { mutableStateOf(0.8f) }
            var highGain by remember { mutableStateOf(false) }
            var eqFlat by remember { mutableStateOf(true) }

            fun playAt(i: Int) {
                if (i in songs.indices) {
                    idx = i
                    player.setMediaItem(MediaItem.fromUri(songs[i].toURI().toString()))
                    player.prepare()
                    player.play()
                }
            }

            LaunchedEffect(volume) {
                player.volume = volume
            }

            LaunchedEffect(player) {
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) {
                        isPlay = p
                    }
                    override fun onPlaybackStateChanged(s: Int) {
                        dur = player.duration.coerceAtLeast(0L)
                    }
                })
                while (true) {
                    pos = player.currentPosition
                    dur = player.duration.coerceAtLeast(0L)
                    if (pos >= dur - 500 && dur > 1000 && idx + 1 < songs.size) {
                        playAt(idx + 1)
                    }
                    delay(200)
                }
            }

            LaunchedEffect(player.audioSessionId) {
                try {
                    val sessionId = player.audioSessionId
                    if (sessionId != android.media.audiofx.AudioEffect.ERROR_BAD_VALUE) {
                        eq = Equalizer(0, sessionId).apply { enabled = true }
                        loud = LoudnessEnhancer(sessionId).apply { enabled = false }
                        viz = Visualizer(sessionId).apply {
                            captureSize = Visualizer.getCaptureSizeRange()[1]
                            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                                override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}
                                override fun onFftDataCapture(v: Visualizer?, f: ByteArray?, r: Int) {
                                    f?.let {
                                        val list = it.take(50).map { b ->
                                            ((b.toInt() and 0xFF) - 128).let { kotlin.math.abs(it) / 35f }.coerceIn(0.05f, 1f)
                                        }
                                        fft = list
                                        vuL = list.take(15).average().toFloat()
                                        vuR = list.takeLast(15).average().toFloat()
                                    }
                                }
                            }, Visualizer.getMaxCaptureRate() / 2, false, true)
                            enabled = true
                        }
                    }
                } catch (_: Exception) {}
            }

            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "FLAC 24-bit 96kHz",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A2A2A))
                                .padding(6.dp)
                        )
                        Text(
                            text = songs.getOrNull(idx)?.name ?: "Dream impossible.wav",
                            color = Color.White,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 8.dp),
                            maxLines = 1
                        )
                        Text(text = "PCM • 96.0 kHz • 24BIT • STEREO", color = Color.Gray, fontSize = 10.sp)

                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(vuL to "L", vuR to "R").forEach { (level, label) ->
                                Box(
                                    Modifier.size(150.dp).clip(RoundedCornerShape(75.dp)).background(Color(0xFF1A1A1A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(Modifier.size(130.dp)) {
                                        drawCircle(
                                            Color(0xFFC9A84C),
                                            radius = size.minDimension / 2,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx())
                                        )
                                        for (db in -20..3 step 2) {
                                            val ang = -110 + ((db + 20) / 23f) * 220f
                                            val rad = Math.toRadians(ang.toDouble() - 90)
                                            val r1 = size.minDimension / 2 - 8
                                            val r2 = size.minDimension / 2 - 16
                                            val col = if (db >= 0) Color.Red else Color(0xFFC9A84C)
                                            drawLine(
                                                col,
                                                center + Offset(cos(rad).toFloat() * r2, sin(rad).toFloat() * r2),
                                                center + Offset(cos(rad).toFloat() * r1, sin(rad).toFloat() * r1),
                                                1.5.dp.toPx()
                                            )
                                        }
                                        val ang = -110 + level.coerceIn(0f, 1f) * 220f
                                        val rad = Math.toRadians(ang.toDouble() - 90)
                                        val x = center.x + cos(rad).toFloat() * 50
                                        val y = center.y + sin(rad).toFloat() * 50
                                        drawLine(Color(0xFFFFE082), center, Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                                        drawCircle(Color.Black, 12.dp.toPx())
                                    }
                                    Text(
                                        text = label,
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().height(85.dp).padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            fft.forEach { h ->
                                Box(
                                    Modifier.width(4.dp).height((4 + h * 75).dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (h > 0.85f) Color.Red else Color(0xFF00E5FF))
                                )
                            }
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            fun f(m: Long) = "%02d:%02d".format((m / 1000 / 60).toInt(), (m / 1000 % 60).toInt())
                            Text(text = f(pos), color = Color.White, fontSize = 13.sp)
                            Text(text = "-" + f((dur - pos).coerceAtLeast(0L)), color = Color.Gray, fontSize = 13.sp)
                        }

                        Slider(
                            value = if (dur > 0) pos.toFloat() / dur else 0f,
                            onValueChange = { player.seekTo((it * dur).toLong()) },
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF00E5FF),
                                thumbColor = Color(0xFF7C4DFF)
                            )
                        )

                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🔈", fontSize = 14.sp)
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFFC9A84C),
                                    thumbColor = Color(0xFFFFE082)
                                )
                            )
                            Text(text = "${(volume * 100).toInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(36.dp))
                            Text(text = "🔊", fontSize = 14.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            FilterChip(
                                selected = highGain,
                                onClick = {
                                    highGain = !highGain
                                    try {
                                        loud?.enabled = highGain
                                        if (highGain) loud?.setTargetGain(900)
                                    } catch (_: Exception) {}
                                },
                                label = { Text("HIGH GAIN") }
                            )
                            FilterChip(
                                selected = !eqFlat,
                                onClick = {
                                    eqFlat = !eqFlat
                                    try {
                                        eq?.enabled = true
                                        val n = eq?.numberOfBands ?: 0
                                        for (i in 0 until n) {
                                            eq?.setBandLevel(i.toShort(), if (eqFlat) 0 else 1000)
                                        }
                                    } catch (_: Exception) {}
                                },
                                label = { Text(if (eqFlat) "EQ FLAT" else "EQ ROCK") }
                            )
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (idx > 0) playAt(idx - 1) }) {
                                Text(text = "⏮", color = Color.White, fontSize = 28.sp)
                            }
                            Button(
                                onClick = {
                                    if (player.isPlaying) player.pause()
                                    else {
                                        if (idx == -1 && songs.isNotEmpty()) playAt(0)
                                        else player.play()
                                    }
                                },
                                modifier = Modifier.size(64.dp),
                                shape = RoundedCornerShape(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                            ) {
                                Text(text = if (isPlay) "⏸" else "▶", fontSize = 24.sp)
                            }
                            IconButton(onClick = { if (idx + 1 < songs.size) playAt(idx + 1) }) {
                                Text(text = "⏭", color = Color.White, fontSize = 28.sp)
                            }
                            IconButton(onClick = { volume = (volume - 0.1f).coerceAtLeast(0f) }) {
                                Text(text = "➖", color = Color.White)
                            }
                            IconButton(onClick = { volume = (volume + 0.1f).coerceAtMost(1f) }) {
                                Text(text = "➕", color = Color.White)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = {
                                val list = mutableListOf<File>()
                                ctx.contentResolver.query(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    arrayOf(MediaStore.Audio.Media.DATA),
                                    null, null, null
                                )?.use { c ->
                                    val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                    while (c.moveToNext()) {
                                        val f = File(c.getString(id) ?: continue)
                                        if (f.exists()) list.add(f)
                                    }
                                }
                                songs = list
                            }) { Text("Interna") }

                            Button(onClick = {
                                val d = File("/storage/emulated/0/Music")
                                songs = d.listFiles()?.filter {
                                    it.extension.lowercase() in listOf("mp3", "flac", "wav", "m4a")
                                }?.sortedBy { it.name } ?: emptyList()
                            }) { Text("Pasta") }
                        }

                        LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
                            itemsIndexed(songs) { i, f ->
                                val sel = i == idx
                                TextButton(
                                    onClick = { playAt(i) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) Color(0xFF1A2333) else Color.Transparent)
                                ) {
                                    Text(
                                        text = f.name,
                                        color = if (sel) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { viz?.enabled = false } catch (_: Exception) {}
        eq?.release()
        loud?.release()
        viz?.release()
        player.release()
    }
}
