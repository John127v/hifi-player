package com.example.hifiplayer

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlin.math.hypot
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private var viz: Visualizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()

        setContent {
            val ctx = LocalContext.current
            var songs by remember { mutableStateOf(listOf<File>()) }
            var idx by remember { mutableStateOf(-1) }
            var isPlay by remember { mutableStateOf(false) }
            var pos by remember { mutableStateOf(0L) }
            var dur by remember { mutableStateOf(0L) }

            // LEITURAS REAIS EM TEMPO REAL
            var fftValues by remember { mutableStateOf(List(32) { 0.05f }) }
            var vuLeft by remember { mutableStateOf(0.05f) }
            var vuRight by remember { mutableStateOf(0.05f) }

            var volume by remember { mutableStateOf(0.8f) }
            var highGain by remember { mutableStateOf(false) }
            var eqFlat by remember { mutableStateOf(true) }

            // INICIALIZAÇÃO DO VISUALIZER / EQUALIZADOR
            fun setupAudioEffects(sessionId: Int) {
                if (sessionId == 0) return

                // Liberar instância anterior se existir
                try {
                    viz?.enabled = false
                    viz?.release()
                    viz = null
                } catch (_: Exception) {}

                // Verificar permissão de áudio antes de instanciar o Visualizer
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        viz = Visualizer(sessionId).apply {
                            captureSize = Visualizer.getCaptureSizeRange()[1]

                            setDataCaptureListener(
                                object : Visualizer.OnDataCaptureListener {
                                    // 1. LEITURA REAL DO VU METER (Waveform - RMS)
                                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                                        waveform?.let { bytes ->
                                            if (bytes.isEmpty()) return
                                            
                                            var sumL = 0.0
                                            var sumR = 0.0
                                            val half = bytes.size / 2

                                            for (i in 0 until half) {
                                                val sampleL = (bytes[i].toInt() and 0xFF) - 128
                                                sumL += (sampleL * sampleL).toDouble()
                                            }
                                            for (i in half until bytes.size) {
                                                val sampleR = (bytes[i].toInt() and 0xFF) - 128
                                                sumR += (sampleR * sampleR).toDouble()
                                            }

                                            val rmsL = Math.sqrt(sumL / half) / 128.0
                                            val rmsR = Math.sqrt(sumR / half) / 128.0

                                            vuLeft = rmsL.toFloat().coerceIn(0.05f, 1.0f)
                                            vuRight = rmsR.toFloat().coerceIn(0.05f, 1.0f)
                                        }
                                    }

                                    // 2. LEITURA REAL DO ANALISADOR GRÁFICO (FFT)
                                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                        fft?.let { bytes ->
                                            if (bytes.size < 64) return
                                            val count = 32
                                            val bands = FloatArray(count)

                                            for (i in 0 until count) {
                                                val r = bytes[2 * i].toInt()
                                                val img = bytes[2 * i + 1].toInt()
                                                val magnitude = hypot(r.toDouble(), img.toDouble()).toFloat()
                                                bands[i] = (magnitude / 50f).coerceIn(0.05f, 1.0f)
                                            }
                                            fftValues = bands.toList()
                                        }
                                    }
                                },
                                Visualizer.getMaxCaptureRate() / 2,
                                true, // Ativa Waveform (VU)
                                true  // Ativa FFT (Analisador)
                            )
                            enabled = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Configuração do Equalizador e Gain
                try {
                    eq?.release()
                    loud?.release()
                    eq = Equalizer(0, sessionId).apply { enabled = true }
                    loud = LoudnessEnhancer(sessionId).apply { enabled = highGain }
                } catch (_: Exception) {}
            }

            // PERMISSÕES EM TEMPO DE EXECUÇÃO
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                if (perms[Manifest.permission.RECORD_AUDIO] == true && player.audioSessionId != 0) {
                    setupAudioEffects(player.audioSessionId)
                }
            }

            LaunchedEffect(Unit) {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                permissionLauncher.launch(perms.toTypedArray())
            }

            // REPRODUÇÃO DE MÚSICA
            fun playAt(i: Int) {
                if (i in songs.indices) {
                    idx = i
                    player.setMediaItem(MediaItem.fromUri(songs[i].toURI().toString()))
                    player.prepare()
                    player.play()
                }
            }

            LaunchedEffect(volume) { player.volume = volume }

            // MONITORAMENTO DO PLAYER E CICLO DO ÁUDIO
            LaunchedEffect(player) {
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) {
                        isPlay = p
                        if (p && viz == null) {
                            setupAudioEffects(player.audioSessionId)
                        }
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

            // ESTILIZAÇÃO VISUAL HI-FI
            val bgDark = Color(0xFF0A0A0C)
            val cardBg = Color(0xFF14161C)
            val cyanNeon = Color(0xFF00E5FF)
            val goldDial = Color(0xFFC9A84C)

            MaterialTheme {
                Box(Modifier.fillMaxSize().background(bgDark)) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {

                        // CABEÇALHO
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("←", color = Color.White, fontSize = 20.sp)
                            Text("NOW PLAYING", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("•••", color = Color.White, fontSize = 18.sp)
                        }

                        Spacer(Modifier.height(8.dp))

                        // INFO DA MÚSICA
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .border(1.dp, Color(0xFF222630), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = songs.getOrNull(idx)?.name ?: "Selecione uma faixa abaixo",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "REALTIME AUDIO STREAM • 96kHz / 24BIT",
                                    color = cyanNeon,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // PAINEL ANALÓGICO (VU + FFT)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF12141A), Color(0xFF08090C))))
                                .border(1.dp, Color(0xFF222530), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                // MOSTRADORES VU L / R
                                Row(
                                    Modifier.fillMaxWidth().weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    VUMeterDial(level = vuLeft, label = "LEFT", dialColor = goldDial)
                                    VUMeterDial(level = vuRight, label = "RIGHT", dialColor = goldDial)
                                }

                                Spacer(Modifier.height(8.dp))

                                // ANALISADOR GRÁFICO (BARRAS FFT)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    fftValues.forEach { h ->
                                        val animH by animateFloatAsState(
                                            targetValue = h,
                                            animationSpec = tween(50),
                                            label = "fft"
                                        )
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .padding(horizontal = 1.dp)
                                                .fillMaxHeight(animH)
                                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                                .background(
                                                    if (animH > 0.85f) Color.Red
                                                    else Brush.verticalGradient(listOf(cyanNeon, cyanNeon.copy(alpha = 0.2f)))
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // TEMPO E PROGRESSO
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            fun f(m: Long) = "%02d:%02d".format((m / 1000 / 60).toInt(), (m / 1000 % 60).toInt())
                            Text(text = f(pos), color = Color.White, fontSize = 11.sp)
                            Text(text = "-" + f((dur - pos).coerceAtLeast(0L)), color = Color.Gray, fontSize = 11.sp)
                        }

                        Slider(
                            value = if (dur > 0) pos.toFloat() / dur else 0f,
                            onValueChange = { player.seekTo((it * dur).toLong()) },
                            colors = SliderDefaults.colors(
                                activeTrackColor = cyanNeon,
                                thumbColor = Color(0xFF7C4DFF)
                            )
                        )

                        // CONTROLE DE VOLUME
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔈", fontSize = 12.sp)
                            Slider(
                                value = volume,
                                onValueChange = { volume = it },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = goldDial,
                                    thumbColor = Color(0xFFFFE082)
                                )
                            )
                            Text("${(volume * 100).toInt()}%", color = Color.White, fontSize = 10.sp, modifier = Modifier.width(36.dp))
                        }

                        // SWITCHES / EQUALIZADOR
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = highGain,
                                onClick = {
                                    highGain = !highGain
                                    try {
                                        loud?.enabled = highGain
                                        if (highGain) loud?.setTargetGain(1000)
                                    } catch (_: Exception) {}
                                },
                                label = { Text("HIGH GAIN", fontSize = 10.sp) }
                            )

                            FilterChip(
                                selected = !eqFlat,
                                onClick = {
                                    eqFlat = !eqFlat
                                    try {
                                        eq?.enabled = true
                                        val n = eq?.numberOfBands ?: 0
                                        for (i in 0 until n) {
                                            eq?.setBandLevel(i.toShort(), if (eqFlat) 0 else 800)
                                        }
                                    } catch (_: Exception) {}
                                },
                                label = { Text(if (eqFlat) "EQ FLAT" else "EQ BOOST", fontSize = 10.sp) }
                            )
                        }

                        // BOTÕES DE REPRODUÇÃO
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (idx > 0) playAt(idx - 1) }) {
                                Text("⏮", color = Color.White, fontSize = 24.sp)
                            }
                            Button(
                                onClick = {
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        if (idx == -1 && songs.isNotEmpty()) playAt(0)
                                        else {
                                            player.play()
                                            setupAudioEffects(player.audioSessionId)
                                        }
                                    }
                                },
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = cyanNeon)
                            ) {
                                Text(if (isPlay) "⏸" else "▶", color = Color.Black, fontSize = 18.sp)
                            }
                            IconButton(onClick = { if (idx + 1 < songs.size) playAt(idx + 1) }) {
                                Text("⏭", color = Color.White, fontSize = 24.sp)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // MENUS / ESCANEAR MEMÓRIA INTERNA E PASTA
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val list = mutableListOf<File>()
                                    ctx.contentResolver.query(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                        arrayOf(MediaStore.Audio.Media.DATA),
                                        null, null, null
                                    )?.use { c ->
                                        val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                        while (c.moveToNext()) {
                                            val path = c.getString(id) ?: continue
                                            val f = File(path)
                                            if (f.exists()) list.add(f)
                                        }
                                    }
                                    songs = list
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Memória Interna", fontSize = 10.sp)
                            }

                            Button(
                                onClick = {
                                    val d = File("/storage/emulated/0/Music")
                                    songs = d.listFiles()?.filter {
                                        it.extension.lowercase() in listOf("mp3", "flac", "wav", "m4a")
                                    }?.sortedBy { it.name } ?: emptyList()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pasta Music", fontSize = 10.sp)
                            }
                        }

                        // LISTA DE MÚSICAS ENCONTRADAS
                        LazyColumn(Modifier.weight(0.7f).padding(top = 4.dp)) {
                            itemsIndexed(songs) { i, f ->
                                val sel = i == idx
                                TextButton(
                                    onClick = { playAt(i) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) Color(0xFF1A2A3A) else Color.Transparent)
                                ) {
                                    Text(
                                        text = f.name,
                                        color = if (sel) cyanNeon else Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

// COMPONENTE DO VU METER ANALÓGICO
@Composable
fun VUMeterDial(level: Float, label: String, dialColor: Color) {
    val animLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(40),
        label = "vu"
    )

    Box(
        Modifier
            .size(125.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF20232A), Color(0xFF101115))))
            .border(2.dp, dialColor, CircleShape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2 + 10)
            val radius = size.minDimension / 2 - 12

            for (db in -20..3 step 2) {
                val ang = -110 + ((db + 20) / 23f) * 220f
                val rad = Math.toRadians(ang.toDouble() - 90)
                val r1 = radius - 4
                val r2 = radius - 12
                val col = if (db >= 0) Color.Red else dialColor

                drawLine(
                    col,
                    centerOffset + Offset(cos(rad).toFloat() * r2, sin(rad).toFloat() * r2),
                    centerOffset + Offset(cos(rad).toFloat() * r1, sin(rad).toFloat() * r1),
                    1.5.dp.toPx()
                )
            }

            val ang = -110 + animLevel.coerceIn(0f, 1f) * 220f
            val rad = Math.toRadians(ang.toDouble() - 90)
            val x = centerOffset.x + cos(rad).toFloat() * (radius - 10)
            val y = centerOffset.y + sin(rad).toFloat() * (radius - 10)

            drawLine(Color(0xFFFFE082), centerOffset, Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.Black, 10.dp.toPx(), centerOffset)
        }

        Text(
            text = label,
            color = Color.Gray,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        )
    }
}
