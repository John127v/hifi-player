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
import androidx.compose.ui.text.font.FontWeight
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()){}.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
        setContent {
            val ctx = LocalContext.current
            var songs by remember { mutableStateOf(listOf<File>()) }
            var idx by remember { mutableStateOf(-1) }
            var isPlay by remember { mutableStateOf(false) }
            var pos by remember { mutableStateOf(0L) }
            var dur by remember { mutableStateOf(0L) }
            var fft by remember { mutableStateOf(List(50){0.08f}) }
            var vuL by remember { mutableStateOf(0.2f) }
            var vuR by remember { mutableStateOf(0.2f) }
            var volume by remember { mutableStateOf(0.8f) }
            var highGain by remember { mutableStateOf(true) }
            fun playAt(i:Int){ if(i in songs.indices){ idx=i; player.setMediaItem(MediaItem.fromUri(songs[i].toURI().toString())); player.prepare(); player.play() } }
            LaunchedEffect(volume){ player.volume=volume }
            LaunchedEffect(player){
                player.addListener(object: Player.Listener{ override fun onIsPlayingChanged(p:Boolean){ isPlay=p } override fun onPlaybackStateChanged(s:Int){ dur=player.duration.coerceAtLeast(0L) } })
                while(true){ pos=player.currentPosition; dur=player.duration.coerceAtLeast(0L); if(pos>=dur-600 && dur>1000 && idx+1<songs.size) playAt(idx+1); delay(200) }
            }
            LaunchedEffect(player.audioSessionId){
                try{
                    eq=Equalizer(0,player.audioSessionId).apply{enabled=true}
                    loud=LoudnessEnhancer(player.audioSessionId).apply{enabled=highGain}
                    viz=Visualizer(player.audioSessionId).apply{
                        captureSize=Visualizer.getCaptureSizeRange()[1]
                        setDataCaptureListener(object:Visualizer.OnDataCaptureListener{
                            override fun onWaveFormDataCapture(v:Visualizer?,w:ByteArray?,r:Int){}
                            override fun onFftDataCapture(v:Visualizer?,f:ByteArray?,r:Int){ f?.let{ val l=it.take(50).map{ b-> ((b.toInt() and 0xFF)-128).let{ kotlin.math.abs(it)/32f }.coerceIn(0.05f,1f) }; fft=l; vuL=l.take(12).average().toFloat(); vuR=l.takeLast(12).average().toFloat() } }
                        }, Visualizer.getMaxCaptureRate()/2, false, true)
                        enabled=true
                    }
                }catch(_:Exception){}
            }
            MaterialTheme(colorScheme=darkColorScheme(background=Color(0xFF0A0A0A))){
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))){
                Column(Modifier.padding(12.dp).fillMaxSize()){
                    // HEADER igual foto
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)).padding(12.dp)){
                        Column{
                            Text("NOW PLAYING", color=Color(0xFFC9A84C), fontSize=10.sp, letterSpacing=2.sp, modifier=Modifier.align(Alignment.CenterHorizontally))
                            Text(songs.getOrNull(idx)?.name?:"Dream impossible.wav", color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=4.dp))
                            Text("Dreamscape • Impossible Dreams • 24-bit/96kHz FLAC", color=Color.Gray, fontSize=11.sp)
                        }
                    }
                    // VU METERS igual foto - L R com -20 e +3
                    Box(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)).padding(8.dp)){
                        Column{
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){ Text("L", color=Color(0xFF00E5FF), fontSize=12.sp); Text("R", color=Color(0xFF00E5FF), fontSize=12.sp) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly){
                                listOf(vuL, vuR).forEach{ level->
                                    Box(Modifier.size(140.dp), contentAlignment=Alignment.Center){
                                        Canvas(Modifier.fillMaxSize()){
                                            drawCircle(Color(0xFFC9A84C), radius=size.minDimension/2, style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                                            // escala igual foto
                                            for(i in -20..3){ val ang=-130 + ((i+20)/23f)*260f; val rad=Math.toRadians(ang.toDouble()-90); val r1=size.minDimension/2-6; val r2=size.minDimension/2-14; val col=if(i>0) Color.Red else Color.White; drawLine(col, center+Offset(cos(rad).toFloat()*r2, sin(rad).toFloat()*r2), center+Offset(cos(rad).toFloat()*r1, sin(rad).toFloat()*r1), 1f.dp.toPx()) }
                                            val ang=-130+level.coerceIn(0f,1f)*260f; val rad=Math.toRadians(ang.toDouble()-90); val x=center.x+cos(rad).toFloat()*45; val y=center.y+sin(rad).toFloat()*45; drawLine(Color.Black, center, Offset(x,y), 3.dp.toPx(), cap=StrokeCap.Round); drawCircle(Color(0xFF222222), 10.dp.toPx())
                                        }
                                        Column(Modifier.align(Alignment.Center).padding(top=30.dp), horizontalAlignment=Alignment.CenterHorizontally){ Text("dB", color=Color.Gray, fontSize=8.sp); Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){ Text("-20", color=Color.White, fontSize=9.sp); Text("+3", color=Color.Red, fontSize=9.sp) } }
                                    }
                                }
                            }
                        }
                    }
                    // ANALISADOR CIANO igual foto
                    Box(Modifier.fillMaxWidth().height(130.dp).padding(top=8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D0D0D))){
                        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment=Alignment.Bottom, horizontalArrangement=Arrangement.SpaceEvenly){
                            fft.forEach{ h-> Box(Modifier.width(3.dp).height((5+h*110).dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4DD0E1).copy(alpha=0.5f + h*0.5f))) }
                        }
                    }
                    // TIMELINE 01:23 -02:22
                    Column(Modifier.padding(top=8.dp)){
                        Slider(value=if(dur>0) pos.toFloat()/dur else 0f, onValueChange={ player.seekTo((it*dur).toLong()) }, colors=SliderDefaults.colors(activeTrackColor=Color(0xFF7DD3D8), inactiveTrackColor=Color(0xFF2A2A2A), thumbColor=Color(0xFF7DD3D8)))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){ fun f(m:Long)="%02d:%02d".format((m/1000/60).toInt(),(m/1000%60).toInt()); Text(f(pos), color=Color.Gray, fontSize=12.sp); Text("-"+f((dur-pos).coerceAtLeast(0L)), color=Color.Gray, fontSize=12.sp) }
                    }
                    // VOLUME NOVO
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)).padding(horizontal=10.dp, vertical=4.dp), verticalAlignment=Alignment.CenterVertically){
                        Text("🔈", fontSize=12.sp); Slider(value=volume, onValueChange={volume=it}, modifier=Modifier.weight(1f), colors=SliderDefaults.colors(activeTrackColor=Color(0xFFC9A84C), thumbColor=Color.White)); Text("${(volume*100).toInt()}%", color=Color.White, fontSize=11.sp)
                    }
                    // CONTROLES << || >>
                    Row(Modifier.fillMaxWidth().padding(top=8.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically){
                        IconButton(onClick={ if(idx>0) playAt(idx-1) }){ Text("◀◀", color=Color(0xFF4A6A6A), fontSize=22.sp) }
                        Button(onClick={ if(player.isPlaying) player.pause() else { if(idx==-1 && songs.isNotEmpty()) playAt(0) else player.play() } }, modifier=Modifier.size(56.dp), shape=RoundedCornerShape(28.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF4DD0E1))){ Text(if(isPlay)"❚❚" else "▶", color=Color.Black, fontSize=18.sp) }
                        IconButton(onClick={ if(idx+1<songs.size) playAt(idx+1) }){ Text("▶▶", color=Color(0xFF4A6A6A), fontSize=22.sp) }
                    }
                    // RODAPÉ igual foto
                    Column(Modifier.fillMaxWidth().padding(top=12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F0F0F)).padding(10.dp)){
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){
                            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)){ Switch(checked=highGain, onCheckedChange={ highGain=it; try{loud?.enabled=it; if(it) loud?.setTargetGain(800)}catch(_:Exception){} }, modifier=Modifier.size(28.dp)); Text("HIGH GAIN", color=Color.Gray, fontSize=10.sp) }
                            Text("EQ: FLAT", color=Color.Gray, fontSize=10.sp); Text("FILTER: PCM", color=Color.Gray, fontSize=10.sp)
                        }
                        Text("Bitrate: 4608 kbps • Sample Rate: 96kHz", color=Color.Gray, fontSize=10.sp, modifier=Modifier.padding(top=6.dp).align(Alignment.CenterHorizontally))
                    }
                    // LISTA
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.padding(top=8.dp)){
                        Button(onClick={ val l=mutableListOf<File>(); ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use{ c-> val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA); while(c.moveToNext()){ val f=File(c.getString(id)?:continue); if(f.exists()) l.add(f) } }; songs=l }, modifier=Modifier.height(32.dp)){ Text("Interna", fontSize=10.sp) }
                        Button(onClick={ val d=File("/storage/emulated/0/Music"); songs=d.listFiles()?.filter{ it.extension.lowercase() in listOf("mp3","flac","wav","m4a") }?.sortedBy{it.name}?: emptyList() }, modifier=Modifier.height(32.dp)){ Text("Pasta", fontSize=10.sp) }
                    }
                    LazyColumn(Modifier.weight(1f)){ itemsIndexed(songs){ i,f-> val sel=i==idx; TextButton(onClick={playAt(i)}){ Text(f.name, color=if(sel) Color(0xFF4DD0E1) else Color.White, fontSize=11.sp, maxLines=1) } } }
                }
            }}
        }
    }
    override fun onDestroy(){ super.onDestroy(); try{viz?.enabled=false}catch(_:Exception){}; eq?.release(); loud?.release(); viz?.release(); player.release() }
}
