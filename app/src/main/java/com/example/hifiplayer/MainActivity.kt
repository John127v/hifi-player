package com.whitelabel.hifiplayer

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlin.math.hypot

enum class RepeatMode { OFF, ALL, ONE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HiFiPlayerApp() }
    }
}

@Composable
fun HiFiPlayerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cyanNeon = Color(0xFF00E5FF)
    val cardBg = Color(0xFF121821)
    val borderNeon = Color(0xFF1E3A4A)

    val songs = remember { listOf("Faixa 01 - White Label Mix", "Faixa 02 - Deep Bass", "Faixa 03 - Hi-Fi Test") }
    var idx by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var shuffleMode by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var fftValues by remember { mutableStateOf(List(64){0.05f}) }
    var vuLeft by remember { mutableFloatStateOf(0.3f) }
    var vuRight by remember { mutableFloatStateOf(0.3f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ hasAudioPermission=it }
    LaunchedEffect(Unit){ if(!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    fun loadSong(i:Int){ idx=i; isPlaying=true }

    DisposableEffect(hasAudioPermission, player?.audioSessionId){
        val viz = if(hasAudioPermission) runCatching{
            Visualizer(player?.audioSessionId?:0).apply{
                captureSize=Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object: Visualizer.OnDataCaptureListener{
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int){}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int){
                        fft?.let{
                            val bands=MutableList(64){0f}; var j=2
                            for(k in bands.indices){ if(j>=it.size-1) break
                                bands[k]=(hypot(it[j].toDouble(), it[j+1].toDouble())/28f).toFloat().coerceIn(0.05f,1.1f); j+=2
                            }
                            fftValues=bands
                            vuLeft=(bands.take(8).average()*1.8).toFloat().coerceIn(0f,1f)
                            vuRight=(bands.drop(8).take(8).average()*1.8).toFloat().coerceIn(0f,1f)
                        }
                    }
                }, Visualizer.getMaxCaptureRate()/2, false, true); enabled=true
            }.getOrNull()
        }else null
        onDispose{ runCatching{viz?.enabled=false; viz?.release()} }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF070A10)).padding(12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)){

        // HEADER
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F1219)).border(1.dp,borderNeon,RoundedCornerShape(14.dp)).padding(12.dp)){
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically){
                Icon(Icons.Filled.Menu,"Menu", tint=cyanNeon, modifier=Modifier.size(24.dp))
                Text("HI-FI PLAYER", color=cyanNeon, fontSize=15.sp, fontWeight=FontWeight.Black, letterSpacing=1.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(showEq) Color(0xFF102030) else Color.Transparent).border(1.dp, if(showEq) cyanNeon else Color(0xFF2A3445), RoundedCornerShape(6.dp)).clickable{showEq=!showEq}.padding(6.dp)){ Icon(Icons.Filled.Equalizer,"EQ", tint=if(showEq) cyanNeon else Color.Gray, modifier=Modifier.size(18.dp)) }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(shuffleMode) Color(0xFF102030) else Color.Transparent).border(1.dp, if(shuffleMode) cyanNeon else Color(0xFF2A3445), RoundedCornerShape(6.dp)).clickable{shuffleMode=!shuffleMode}.padding(6.dp)){ Icon(Icons.Filled.Shuffle,"RND", tint=if(shuffleMode) cyanNeon else Color.Gray, modifier=Modifier.size(18.dp)) }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(repeatMode!=RepeatMode.OFF) Color(0xFF102030) else Color.Transparent).border(1.dp, if(repeatMode!=RepeatMode.OFF) cyanNeon else Color(0xFF2A3445), RoundedCornerShape(6.dp)).clickable{ repeatMode=when(repeatMode){RepeatMode.OFF->RepeatMode.ALL; RepeatMode.ALL->RepeatMode.ONE; RepeatMode.ONE->RepeatMode.OFF}}.padding(6.dp)){
                        Box(contentAlignment=Alignment.Center){
                            Icon(Icons.Filled.Repeat,"REP", tint=if(repeatMode!=RepeatMode.OFF) cyanNeon else Color.Gray, modifier=Modifier.size(18.dp))
                            if(repeatMode==RepeatMode.ONE) Text("1", color=cyanNeon, fontSize=8.sp, fontWeight=FontWeight.Black, modifier=Modifier.offset(y=(-1).dp))
                        }
                    }
                }
            }
        }

        // EQ VERTICAL DESLIZANTE - CORRIGIDO
        if(showEq){
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg).border(1.dp,borderNeon,RoundedCornerShape(12.dp)).padding(12.dp)){
                Column{
                    Text("5-BAND EQUALIZER", color=cyanNeon, fontSize=10.sp, fontWeight=FontWeight.Black)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically){
                        val freqs = listOf("60","230","910","3.6K","14K")
                        freqs.forEachIndexed{ i,f ->
                            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(6.dp)){
                                // Slider vertical real
                                Box(Modifier.height(100.dp).width(30.dp), contentAlignment=Alignment.Center){
                                    Slider(value=0.5f, onValueChange={}, valueRange=0f..1f, modifier=Modifier.rotate(-90f).width(100.dp))
                                }
                                Text(f, color=Color.Gray, fontSize=8.sp, fontWeight=FontWeight.Bold)
                                Text("0dB", color=cyanNeon, fontSize=7.sp)
                            }
                        }
                    }
                }
            }
        }

        // VU BARGRAPH -10 a +3
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("L" to vuLeft, "R" to vuRight).forEach{ (ch, level) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(cardBg).border(1.dp,borderNeon,RoundedCornerShape(12.dp)).padding(8.dp)){
                    Column{
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween){ Text("VU $ch", color=cyanNeon, fontSize=9.sp, fontWeight=FontWeight.Bold); Text("${(level*100).toInt()}%", color=Color.Gray, fontSize=9.sp) }
                        Canvas(Modifier.fillMaxWidth().height(20.dp).padding(top=4.dp)){
                            val steps=listOf(-10,-7,-5,-3,-1,0,1,2,3)
                            val w=size.width/steps.size
                            steps.forEachIndexed{ i,v ->
                                val active=(level*13-10)>=v
                                drawRect(if(active){if(v>=0) Color(0xFFFF1744) else cyanNeon}else Color(0xFF1E2A3A), topLeft=Offset(i*w,0f), size=Size(w-2.dp.toPx(), size.height))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween){ listOf("-10","-5","0","+3").forEach{ Text(it, color=Color.Gray, fontSize=7.sp)} }
                    }
                }
            }
        }

        // SPECTRUM 16 FAIXAS
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg).border(1.dp,borderNeon,RoundedCornerShape(12.dp)).padding(8.dp)){
            Column{
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween){
                    Text("SPECTRUM • 16-BAND", color=cyanNeon, fontSize=10.sp, fontWeight=FontWeight.Black)
                    Text("FAST • PEAK ON", color=Color.Gray, fontSize=7.sp, modifier=Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, cyanNeon.copy(alpha=0.3f), RoundedCornerShape(10.dp)).padding(horizontal=6.dp, vertical=2.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF080A0F))){
                    Canvas(Modifier.fillMaxSize()){
                        val bands=16; val barW=size.width/bands
                        val grouped=List(bands){ b -> val s=b*fftValues.size/bands; val e=(b+1)*fftValues.size/bands; fftValues.subList(s,e.coerceAtMost(fftValues.size)).maxOrNull()?:0.05f }
                        grouped.forEachIndexed{ i,h ->
                            val bh=size.height*h.coerceIn(0.05f,1f)
                            val col=when{ h>0.9f->Color(0xFFFF1744); h>0.72f->Color(0xFFFFEB3B); else->cyanNeon }
                            drawRoundRect(col, topLeft=Offset(i*barW+3.dp.toPx(), size.height-bh), size=Size(barW-6.dp.toPx(), bh), cornerRadius=CornerRadius(2.dp.toPx()))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=4.dp), Arrangement.SpaceBetween){
                    listOf("31","62","125","250","500","1K","2K","4K","8K","16K").forEach{ Text(it, color=cyanNeon.copy(alpha=0.6f), fontSize=7.sp, fontWeight=FontWeight.Bold) }
                }
            }
        }

        // CONTROLES SKIP TRACK VETORIAL
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically){
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF1A2435)).border(1.dp,borderNeon,CircleShape).clickable{ loadSong(if(idx>0) idx-1 else songs.size-1) }, contentAlignment=Alignment.Center){ Icon(Icons.Filled.SkipPrevious,"Prev", tint=Color.White, modifier=Modifier.size(30.dp)) }
            Box(Modifier.size(74.dp).clip(CircleShape).background(cyanNeon).clickable{ isPlaying=!isPlaying }, contentAlignment=Alignment.Center){ Icon(if(isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,"Play", tint=Color.Black, modifier=Modifier.size(38.dp)) }
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF1A2435)).border(1.dp,borderNeon,CircleShape).clickable{ loadSong(if(idx<songs.size-1) idx+1 else 0) }, contentAlignment=Alignment.Center){ Icon(Icons.Filled.SkipNext,"Next", tint=Color.White, modifier=Modifier.size(30.dp)) }
        }

        // PLAYLIST
        LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
            itemsIndexed(songs){ i,t ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(i==idx) Color(0xFF102030) else cardBg).border(1.dp, if(i==idx) cyanNeon else borderNeon, RoundedCornerShape(10.dp)).clickable{loadSong(i)}.padding(12.dp)){
                    Text(t, color=if(i==idx) cyanNeon else Color.White, fontSize=13.sp, fontWeight=if(i==idx) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
