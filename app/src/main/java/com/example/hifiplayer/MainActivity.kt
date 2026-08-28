package com.whitelabel.hifiplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.hypot

data class Song(val id: Long, val title: String, val uri: Uri)

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
    val cyanNeon = Color(0xFF00E5FF)
    val cardBg = Color(0xFF121821)
    val borderNeon = Color(0xFF1E3A4A)

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var idx by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var shuffleMode by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var eqLevels by remember { mutableStateOf(List(5){0.5f}) }
    var fftValues by remember { mutableStateOf(List(64){0.05f}) }
    var vuLeft by remember { mutableFloatStateOf(0.1f) }
    var vuRight by remember { mutableFloatStateOf(0.1f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }

    val permission = if(Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission)==PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ hasPerm=it }

    // CARREGAR MUSICAS DO CELULAR
    fun loadMusics(){
        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        val sel = "${MediaStore.Audio.Media.IS_MUSIC}!= 0"
        context.contentResolver.query(uri, proj, sel, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use{ c ->
            while(c.moveToNext()){
                val id = c.getLong(0); val title = c.getString(1)
                list.add(Song(id, title, ContentUris.withAppendedId(uri, id)))
            }
        }
        songs = list
        if(list.isEmpty()){
            // Fallback se não achar
            songs = listOf(Song(0,"Faixa 01 - White Label Mix", Uri.EMPTY), Song(1,"Faixa 02 - Deep Bass", Uri.EMPTY))
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){ uri ->
        uri?.let{ loadMusics() }
    }

    LaunchedEffect(hasPerm){ if(hasPerm) loadMusics() else permLauncher.launch(permission) }

    fun playSong(i:Int){
        if(songs.isEmpty()) return
        idx=i
        runCatching{
            player?.release()
            val s = songs[i]
            player = MediaPlayer.create(context, s.uri).apply{
                setOnCompletionListener{
                    if(repeatMode==RepeatMode.ONE){ seekTo(0); start() }
                    else { val n=if(idx<songs.size-1) idx+1 else 0; playSong(n) }
                }
                start()
            }
            isPlaying=true
            // RECRIAR EQUALIZER E VISUALIZER COM SESSION NOVA
            equalizer?.release()
            visualizer?.release()
            val session = player!!.audioSessionId
            equalizer = Equalizer(0, session).apply{ enabled=true }
            visualizer = Visualizer(session).apply{
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
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF070A10)).padding(12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)){

        // HEADER COM BOTAO ABRIR PASTA
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F1219)).border(1.dp,borderNeon,RoundedCornerShape(14.dp)).padding(12.dp)){
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically){
                Icon(Icons.Filled.Folder, "Abrir", tint=cyanNeon, modifier=Modifier.size(26.dp).clip(CircleShape).clickable{ folderLauncher.launch(null) }.padding(2.dp))
                Text("HI-FI PLAYER", color=cyanNeon, fontSize=15.sp, fontWeight=FontWeight.Black)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(showEq) Color(0xFF102030) else Color.Transparent).border(1.dp, if(showEq) cyanNeon else Color(0xFF2A3445), RoundedCornerShape(6.dp)).clickable{showEq=!showEq}.padding(6.dp)){ Icon(Icons.Filled.Equalizer,"EQ", tint=if(showEq) cyanNeon else Color.Gray, modifier=Modifier.size(18.dp)) }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(shuffleMode) Color(0xFF102030) else Color.Transparent).border(1.dp, if(shuffleMode) cyanNeon else Color(0xFF2A3445), RoundedCornerShape(6.dp)).clickable{shuffleMode=!shuffleMode}.padding(6.dp)){ Icon(Icons.Filled.Shuffle,"RND", tint=if(shuffleMode) cyanNeon else Color.Gray, modifier=Modifier.size(18.dp)) }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(repeatMode!=RepeatMode.OFF) Color(0xFF102030) else Color.Transparent).border(1.dp, if(repeatMode!=RepeatMode.OFF) cyanNeon else Color(0xFF2A3445), RoundedCornerShape(6.dp)).clickable{ repeatMode=when(repeatMode){RepeatMode.OFF->RepeatMode.ALL; RepeatMode.ALL->RepeatMode.ONE; RepeatMode.ONE->RepeatMode.OFF}}.padding(6.dp)){ Icon(Icons.Filled.Repeat,"REP", tint=if(repeatMode!=RepeatMode.OFF) cyanNeon else Color.Gray, modifier=Modifier.size(18.dp)) }
                }
            }
        }

        if(!hasPerm){
            Button(onClick={permLauncher.launch(permission)}, colors=ButtonDefaults.buttonColors(containerColor=cyanNeon)){ Text("PERMITIR ACESSO AS MUSICAS", color=Color.Black, fontWeight=FontWeight.Black) }
        }

        // EQ VERTICAL COM DESLOCAMENTO TOTAL - CORRIGIDO
        if(showEq){
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg).border(1.dp,borderNeon,RoundedCornerShape(12.dp)).padding(12.dp)){
                Column{
                    Text("5-BAND EQUALIZER • ARRASTE VERTICAL", color=cyanNeon, fontSize=10.sp, fontWeight=FontWeight.Black)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement=Arrangement.SpaceEvenly){
                        val freqs=listOf("60","230","910","3.6K","14K")
                        freqs.forEachIndexed{ i,f ->
                            var level by remember{ mutableStateOf(eqLevels[i]) }
                            Column(horizontalAlignment=Alignment.CenterHorizontally){
                                // SLIDER VERTICAL REAL COM 100% DE DESLOCAMENTO
                                Box(Modifier.width(40.dp).height(120.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF080A0F)).border(1.dp, borderNeon, RoundedCornerShape(20.dp))
                                   .pointerInput(i){
                                        detectVerticalDragGestures{ _, dragAmount ->
                                            val newVal = (level - dragAmount/120f).coerceIn(0f,1f)
                                            level=newVal
                                            val newList=eqLevels.toMutableList(); newList[i]=newVal; eqLevels=newList
                                            equalizer?.let{ eq ->
                                                if(i<eq.numberOfBands){
                                                    val r=eq.bandLevelRange
                                                    eq.setBandLevel(i.toShort(), (r[0] + (r[1]-r[0])*newVal).toInt().toShort())
                                                }
                                            }
                                        }
                                    }
                                ){
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(level).align(Alignment.BottomCenter).background(cyanNeon.copy(alpha=0.3f)))
                                    Box(Modifier.fillMaxWidth().height(4.dp).background(cyanNeon).align(Alignment.BottomCenter).offset(y=-(level*116).dp))
                                    Box(Modifier.size(18.dp).clip(CircleShape).background(cyanNeon).align(Alignment.BottomCenter).offset(y=-(level*112).dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(f, color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Bold)
                                Text("${((level-0.5f)*30).toInt()}dB", color=cyanNeon, fontSize=7.sp)
                            }
                        }
                    }
                }
            }
        }

        // VU + SPECTRUM 16
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("L" to vuLeft, "R" to vuRight).forEach{ (ch, lv) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(cardBg).border(1.dp,borderNeon,RoundedCornerShape(12.dp)).padding(8.dp)){
                    Column{
                        Canvas(Modifier.fillMaxWidth().height(22.dp)){
                            val steps=listOf(-10,-7,-5,-3,-1,0,1,2,3); val w=size.width/steps.size
                            steps.forEachIndexed{ idx2,v -> val active=(lv*13-10)>=v; drawRect(if(active){if(v>=0) Color(0xFFFF1744) else cyanNeon}else Color(0xFF1E2A3A), topLeft=Offset(idx2*w,0f), size=Size(w-2.dp.toPx(), size.height)) }
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween){ listOf("-10","-5","0","+3").forEach{ Text(it, color=Color.Gray, fontSize=7.sp)} }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg).border(1.dp,borderNeon,RoundedCornerShape(12.dp)).padding(8.dp)){
            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF080A0F))){
                Canvas(Modifier.fillMaxSize()){
                    val bands=16; val barW=size.width/bands
                    val grouped=List(bands){ b -> val s=b*fftValues.size/bands; val e=(b+1)*fftValues.size/bands; fftValues.subList(s,e.coerceAtMost(fftValues.size)).maxOrNull()?:0.05f }
                    grouped.forEachIndexed{ i,h -> val bh=size.height*h.coerceIn(0.05f,1f); val col=when{ h>0.9f->Color(0xFFFF1744); h>0.72f->Color(0xFFFFEB3B); else->cyanNeon }; drawRoundRect(col, topLeft=Offset(i*barW+3.dp.toPx(), size.height-bh), size=Size(barW-6.dp.toPx(), bh), cornerRadius=CornerRadius(2.dp.toPx())) }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically){
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF1A2435)).border(1.dp,borderNeon,CircleShape).clickable{ if(songs.isNotEmpty()) playSong(if(idx>0) idx-1 else songs.size-1) }, contentAlignment=Alignment.Center){ Icon(Icons.Filled.SkipPrevious,"Prev", tint=Color.White, modifier=Modifier.size(30.dp)) }
            Box(Modifier.size(74.dp).clip(CircleShape).background(cyanNeon).clickable{ player?.let{ if(isPlaying){ it.pause(); isPlaying=false } else { it.start(); isPlaying=true } } }, contentAlignment=Alignment.Center){ Icon(if(isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,"Play", tint=Color.Black, modifier=Modifier.size(38.dp)) }
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF1A2435)).border(1.dp,borderNeon,CircleShape).clickable{ if(songs.isNotEmpty()) playSong(if(idx<songs.size-1) idx+1 else 0) }, contentAlignment=Alignment.Center){ Icon(Icons.Filled.SkipNext,"Next", tint=Color.White, modifier=Modifier.size(30.dp)) }
        }

        LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
            itemsIndexed(songs){ i,s -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(i==idx) Color(0xFF102030) else cardBg).border(1.dp, if(i==idx) cyanNeon else borderNeon, RoundedCornerShape(10.dp)).clickable{ playSong(i) }.padding(12.dp)){ Text(s.title, color=if(i==idx) cyanNeon else Color.White, fontSize=13.sp) } }
        }
    }
}
