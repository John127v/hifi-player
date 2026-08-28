package com.example.hifiplayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var path by remember { mutableStateOf("/storage") }
                Column(Modifier.padding(16.dp)) {
                    Text("HiFi Player - USB", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    TextField(value = path, onValueChange = { path = it }, label = { Text("Pasta do Pendrive") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { /* scan USB */ }) { Text("Escanear Pendrive") }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) { Text("⏮") }
                        Button(onClick = {}) { Text("▶️ Play") }
                        Button(onClick = {}) { Text("⏭") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Status: Aguardando USB")
                }
            }
        }
    }
}
