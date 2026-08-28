package com.example.hifiplayer

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
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
import androidx.compose.foundation.lazy.items
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
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var eq: Equalizer? = null
    private var viz: Visualizer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()){}.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
        setContent {
            val ctx = LocalContext.current
            var songs by remember { mutableStateOf(listOf<File>()) }
            var now by remember { mutableStateOf<File?>(null) }
            var isPlay by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Pronto") }
            var fft by remember { mutableStateOf(List(50){0f}) }
            LaunchedEffect(player.audioSessionId){
                try{
                    eq=Equalizer(0,player.audioSessionId).apply{enabled=true}
                    viz=Visualizer(player.audioSessionId).apply{
                        captureSize=Visualizer.getCaptureSizeRange()[1]
                        setDataCaptureListener(object:Visualizer.OnDataCaptureListener{
                            override fun onWaveFormDataCapture(v:Visualizer?,w:ByteArray?,r:Int){}
                            override fun onFftDataCapture(v:Visualizer?,f:ByteArray?,r:Int){ f?.let{ fft=it.take(50).map{ b->(b.toInt() and 0xFF)/128f } } }
                        }, Visualizer.getMaxCaptureRate()/2, false, true)
                        enabled=true
                    }
                }catch(_:Exception){}
            }
            MaterialTheme{
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))){
                Column(Modifier.padding(12.dp)){
                    Text("FLAC 24-bit 96kHz", color=Color(0xFF00E5FF), fontSize=11.sp, modifier=Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A2A2A)).padding(6.dp))
                    Text(now?.name?:"Dream impossible.wav", color=Color.White, fontSize=18.sp, modifier=Modifier.padding(top=8.dp))
                    Text("PCM • 96.0 kHz • 24BIT • STEREO", color=Color.Gray, fontSize=10.sp)
                    Row(Modifier.fillMaxWidth().padding(top=12.dp), horizontalArrangement=Arrangement.SpaceEvenly){
                        repeat(2){
                            Box(Modifier.size(140.dp).clip(RoundedCornerShape(70.dp)).background(Color(0xFF1A1A1A)), contentAlignment=Alignment.Center){
                                Canvas(Modifier.size(120.dp)){
                                    drawCircle(Color(0xFFC9A84C), radius=size.minDimension/2, style=androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                                    val level=if(isPlay) fft.random()*0.8f else 0.1f
                                    val angle=-120+level*120
                                    val rad=Math.toRadians(angle.toDouble())
                                    val x=center.x+cos(rad).toFloat()*50
                                    val y=center.y+sin(rad).toFloat()*50
                                    drawLine(Color(0xFFFFE082), center, Offset(x,y), strokeWidth=2.dp.toPx(), cap=StrokeCap.Round)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(80.dp).padding(top=12.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.Bottom){
                        fft.forEach{ h-> Box(Modifier.width(4.dp).height((4+h*70).dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF00E5FF))) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top=12.dp), horizontalArrangement=Arrangement.SpaceBetween){ Text("01:23", color=Color.White); Text("-02:22", color=Color.Gray) }
                    Slider(value=0.3f, onValueChange={}, colors=SliderDefaults.colors(activeTrackColor=Color(0xFF00E5FF)))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(onClick={}, label={Text("HIGH GAIN")}, selected=true)
                        FilterChip(onClick={}, label={Text("EQ FLAT")}, selected=false)
                        Text("BITRATE 4608 kbps", color=Color.Gray, fontSize=10.sp, modifier=Modifier.padding(top=12.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically){
                        IconButton(onClick={}){ Text("⏮", color=Color.White, fontSize=24.sp) }
                        Button(onClick={ if(player.isPlaying){player.pause(); isPlay=false} else {player.play(); isPlay=true} }, modifier=Modifier.size(64.dp), shape=RoundedCornerShape(32.dp)){ Text(if(isPlay)"⏸" else "▶", fontSize=24.sp) }
                        IconButton(onClick={}){ Text("⏭", color=Color.White, fontSize=24.sp) }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.padding(top=8.dp)){
                        Button(onClick={
                            val list=mutableListOf<File>()
                            ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.DATA), "${MediaStore.Audio.Media.IS_MUSIC}!=0", null, null)?.use{ c ->
                                val idx=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                while(c.moveToNext()){ val p=c.getString(idx)?: continue; val f=File(p); if(f.exists()) list.add(f) }
                            }
                            songs=list; status="Interna: ${list.size}"
                        }){ Text("Interna") }
                        Button(onClick={
                            val dir=File("/storage/emulated/0/Music")
                            songs=dir.listFiles()?.filter{ it.extension.lowercase() in listOf("mp3","flac","wav","m4a") }?: emptyList()
                            status="Pasta: ${songs.size}"
                        }){ Text("Pasta") }
                    }
                    Text(status, color=Color.Gray, fontSize=10.sp)
                    LazyColumn{ items(songs){ f -> TextButton(onClick={ player.setMediaItem(MediaItem.fromUri(f.toURI().toString())); player.prepare(); player.play(); isPlay=true; now=f }){ Text(f.name, color=Color.White, fontSize=12.sp) } } }
                }
            }}
        }
    }
    override fun onDestroy(){ super.onDestroy(); eq?.release(); viz?.release(); player.release() }
}
