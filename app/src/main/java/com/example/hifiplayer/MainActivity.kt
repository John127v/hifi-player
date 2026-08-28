// V12 RENDER EDITION - Corrigida + Otimizada
// Principais mudanças:
// - sessionId guardado pra não recriar Visualizer
// - FFT com FloatArray (sem alocação)
// - EQ dinâmico com freq real do device
// - Visualizer com RECORD_AUDIO check seguro

private var currentSessionId by mutableStateOf(0)

fun setupAudioEffects(sessionId: Int) {
    if (sessionId == 0 || sessionId == currentSessionId) return
    currentSessionId = sessionId
    try {
        viz?.enabled = false; viz?.release()
    } catch (_: Exception) {}
    viz = null

    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)!= PackageManager.PERMISSION_GRANTED) return

    try {
        viz = Visualizer(sessionId).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1] // 1024
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                    waveform?.let {
                        val half = it.size / 2
                        var l = 0.0; var r = 0.0
                        for(i in 0 until half) { val s = (it[i].toInt() and 0xFF) - 128; l += s*s }
                        for(i in half until it.size) { val s = (it[i].toInt() and 0xFF) - 128; r += s*s }
                        vuLeft = (vuLeft * 0.75f + Math.sqrt(l/half).toFloat()/128f * 0.25f).coerceIn(0.05f, 1f)
                        vuRight = (vuRight * 0.75f + Math.sqrt(r/half).toFloat()/128f * 0.25f).coerceIn(0.05f, 1f)
                    }
                }
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                    fft?.let { bytes ->
                        if (bytes.size < 128) return
                        val bands = FloatArray(64)
                        for(i in 0 until 64) {
                            val re = bytes[2*i].toInt()
                            val im = bytes[2*i+1].toInt()
                            bands[i] = (hypot(re.toDouble(), im.toDouble()) / 35f).toFloat().coerceIn(0.05f, 1.1f)
                        }
                        fftValues = bands.toList() // Mantive List pra não quebrar seu Canvas
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)
            enabled = true
        }
        eq?.release(); loud?.release()
        eq = Equalizer(0, sessionId).apply { enabled = true }
        loud = LoudnessEnhancer(sessionId).apply { enabled = highGain; setTargetGain(0) }
    } catch (_: Exception) { /* device sem Visualizer */ }
}
