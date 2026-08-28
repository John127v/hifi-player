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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            var fft by remember { mutableStateOf(List(40) { 0.1f }) }
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
                    override fun onIsPlayingChanged(p: Boolean) { isPlay = p }
                    override fun onPlaybackStateChanged(s: Int) { dur = player.duration.coerceAtLeast(0L) }
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
                                        val list = it.take(40).map { b ->
                                            ((b.toInt() and 0xFF) - 128).let { kotlin.math.abs(it) / 35f }.coerceIn(0.05f, 1f)
                                        }
                                        fft = list
                                        vuL = list.take(12).average().toFloat()
                                        vuR = list.takeLast(12).average().toFloat()
                                    }
                                }
                            }, Visualizer.getMaxCaptureRate() / 2, false, true)
                            enabled = true
                        }
                    }
                } catch (_: Exception) {}
            }

            // Cores Audiófilas
            val cyanNeon = Color(0xFF00E5FF)
            val goldDial = Color(0xFFD4AF37)
            val bgDark = Color(0xFF121316)
            val cardBg = Color(0xFF1A1C22)

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = bgDark) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header superior
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "←", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { })
                            Text(
                                text = "NOW PLAYING",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(text = "•••", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { })
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Banner com Informações da Música
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .border(1.dp, Color(0xFF2A2D36), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = songs.getOrNull(idx)?.name ?: "Dream impossible.wav",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Dreamscape • Impossible Dreams • 24-bit/96kHz FLAC",
                                    color = Color(0xFF8E95A5),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Container Analógico (VU Meters + Visualizador FFT)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF16181D), Color(0xFF0E0F12))))
                                .border(1.dp, Color(0xFF282B34), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // VU Meters Analógicos
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1.1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    VUDialAnalog(level = vuL, label = "L", dialColor = goldDial)
                                    VUDialAnalog(level = vuR, label = "R", dialColor = goldDial)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Espectro FFT Neon
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.9f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    fft.forEach { value ->
                                        val animHeight by animateFloatAsState(
                                            targetValue = value,
                                            animationSpec = tween(durationMillis = 100),
                                            label = "fft"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(5.dp)
                                                .fillMaxHeight(animHeight.coerceIn(0.05f, 1f))
                                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(cyanNeon, cyanNeon.copy(alpha = 0.2f))
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Barra de Progresso + Contadores de Tempo
                        Column {
                            Slider(
                                value = if (dur > 0) pos.toFloat() / dur else 0f,
                                onValueChange = { player.seekTo((it * dur).toLong()) },
                                colors = SliderDefaults.colors(
                                    activeTrackColor = cyanNeon,
                                    inactiveTrackColor = Color(0xFF262933),
                                    thumbColor = cyanNeon
                                ),
                                modifier = Modifier.height(20.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                fun formatTime(m: Long) = "%02d:%02d".format((m / 1000 / 60).toInt(), (m / 1000 % 60).toInt())
                                Text(text = formatTime(pos), color = Color.Gray, fontSize = 12.sp)
                                Text(text = "-${formatTime((dur - pos).coerceAtLeast(0L))}", color = Color.Gray, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Controles de Reprodução Estilo Hi-Fi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (idx > 0) playAt(idx - 1) }) {
                                Text(text = "◄◄", color = Color.White, fontSize = 20.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(cyanNeon)
                                    .clickable {
                                        if (player.isPlaying) player.pause()
                                        else {
                                            if (idx == -1 && songs.isNotEmpty()) playAt(0)
                                            else player.play()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPlay) "❚❚" else "►",
                                    color = bgDark,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(onClick = { if (idx + 1 < songs.size) playAt(idx + 1) }) {
                                Text(text = "►►", color = Color.White, fontSize = 20.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Alternadores de Áudio Inferiores (High Gain, EQ, Filter)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = highGain,
                                    onCheckedChange = {
                                        highGain = it
                                        try {
                                            loud?.enabled = highGain
                                            if (highGain) loud?.setTargetGain(900)
                                        } catch (_: Exception) {}
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = bgDark,
                                        checkedTrackColor = cyanNeon,
                                        uncheckedTrackColor = Color(0xFF262933)
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("HIGH GAIN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            TextButton(onClick = {
                                eqFlat = !eqFlat
                                try {
                                    eq?.enabled = true
                                    val n = eq?.numberOfBands ?: 0
                                    for (i in 0 until n) {
                                        eq?.setBandLevel(i.toShort(), if (eqFlat) 0 else 1000)
                                    }
                                } catch (_: Exception) {}
                            }) {
                                Text(if (eqFlat) "EQ: FLAT" else "EQ: ROCK", color = Color.LightGray, fontSize = 11.sp)
                            }

                            Text("FILTER: PCM", color = Color.Gray, fontSize = 11.sp)
                        }

                        // Indicador de Formato Fixo no Rodapé
                        Text(
                            text = "Bitrate: 4608 kbps • Sample Rate: 96kHz",
                            color = Color(0xFF5A6070),
                            fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
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

@Composable
fun VUDialAnalog(level: Float, label: String, dialColor: Color) {
    val animLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(durationMillis = 120),
        label = "vu"
    )

    Box(
        modifier = Modifier
            .size(135.dp)
            .clip(RoundedCornerShape(67.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF22252E), Color(0xFF111216))))
            .border(2.dp, Brush.linearGradient(listOf(dialColor.copy(alpha = 0.6f), Color(0xFF1E2028))), CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2 + 10)
            val radius = size.minDimension / 2 - 12

            // Marcas do Medidor (-20 dB até +3 dB)
            for (db in -20..3 step 4) {
                val fraction = (db + 20) / 23f
                val angle = -135 + fraction * 90f
                val rad = Math.toRadians(angle.toDouble() - 90)

                val innerR = radius - 10
                val outerR = radius - 2

                val color = if (db >= 0) Color(0xFFFF5252) else dialColor

                drawLine(
                    color = color,
                    start = centerOffset + Offset(cos(rad).toFloat() * innerR, sin(rad).toFloat() * innerR),
                    end = centerOffset + Offset(cos(rad).toFloat() * outerR, sin(rad).toFloat() * outerR),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Ponteiro Analógico Dourado
            val pointerAngle = -135 + animLevel.coerceIn(0f, 1f) * 90f
            val pointerRad = Math.toRadians(pointerAngle.toDouble() - 90)
            val pointerLength = radius - 8

            drawLine(
                color = Color(0xFFFFE082),
                start = centerOffset,
                end = centerOffset + Offset(cos(pointerRad).toFloat() * pointerLength, sin(pointerRad).toFloat() * pointerLength),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(Color.Black, 8.dp.toPx(), centerOffset)
            drawCircle(dialColor, 3.dp.toPx(), centerOffset)
        }

        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        )

        Text(
            text = "dB",
            color = Color.Gray,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
