package com.inovatickets.validador.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Vibrator
import android.view.View // Recommended: Add if you use View.VISIBLE/GONE for progress bars
import android.widget.ArrayAdapter // Recommended: Add for Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel // Add this import
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.inovatickets.validador.R // Recommended: Add if you use R.id.your_progress_bar or R.id.your_sector_spinner
import com.inovatickets.validador.databinding.ActivityScannerBinding
import com.inovatickets.validador.ui.viewmodels.ScannerViewModel
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.remote.EventoRemoteDataSource
// --- THIS IS THE NEW IMPORT YOU NEED TO ADD or ensure is present ---
import com.inovatickets.validador.data.repository.SyncRepository

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var viewModel: ScannerViewModel
    private lateinit var capture: CaptureManager
    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private lateinit var toneGenerator: ToneGenerator
    private lateinit var vibrator: Vibrator
    private var isTorchOn: Boolean = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViewModel() // This method will be changed
        initAudioFeedback()
        setupScanner()
        setupObservers() // We will add sector observers here
        setupListeners()
    }

    // --- REPLACE YOUR OLD initViewModel() METHOD WITH THIS ONE ---
    private fun initViewModel() {
        val database = AppDatabase.getDatabase(this)

        var supabaseUrl = "https://fqhbkqslawwpemwguhtw.supabase.co" // Your Supabase URL
        val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZxaGJrcXNsYXd3cGVtd2d1aHR3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM4NTA3ODEsImV4cCI6MjA2OTQyNjc4MX0.fWk-V5LKHsMrDL9Af0WboP6W-VjOb9pl0WRH1SgBMbA" // Your Supabase Key

        if (!supabaseUrl.endsWith("/")) {
            supabaseUrl += "/"
        }

        val eventoRemoteDataSource = EventoRemoteDataSource(supabaseUrl, supabaseKey)
        // Create SyncRepository
        val syncRepository = SyncRepository(eventoRemoteDataSource)

        // Pass SyncRepository to ScannerViewModel using ViewModelProvider.Factory
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ScannerViewModel(database, eventoRemoteDataSource, syncRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[ScannerViewModel::class.java]
    }
    // --- END OF REPLACEMENT FOR initViewModel() ---

    private fun initAudioFeedback() {
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    private fun setupScanner() {
        if (checkCameraPermission()) {
            initializeScanner()
        } else {
            requestCameraPermission()
        }
    }

    private fun initializeScanner() {
        barcodeScannerView = binding.zxingBarcodeScanner
        capture = CaptureManager(this, barcodeScannerView)

        barcodeScannerView.setStatusText("Posicione o QR Code na área de leitura")
        barcodeScannerView.decodeContinuous { result ->
            val codigo = result.text
            // Consider also passing the selected sector ID if you have a sector spinner
            // val selectedSectorId = if (binding.yourSectorSpinner.selectedItemPosition >= 0) {
            //     viewModel.setores.value?.get(binding.yourSectorSpinner.selectedItemPosition)?.id
            // } else {
            //     null
            // }
            // viewModel.validarCodigo(codigo, selectedSectorId) // If you modify validarCodigo to take sectorId
            viewModel.validarCodigo(codigo)
        }

        capture.initializeFromIntent(intent, null)
        capture.decode()
    }

    private fun setupObservers() {
        viewModel.validationResult.observe(this) { result ->
            when {
                result.sucesso -> {
                    playSuccessSound()
                    // You might want to include sector information if available and relevant
                    showSuccess(result.mensagem ?: "Código válido!")
                }
                else -> {
                    playErrorSound()
                    showError(result.mensagem ?: "Código inválido")
                }
            }
        }

        viewModel.eventoAtual.observe(this) { evento ->
            binding.textEventoAtual.text = evento?.nome ?: "Nenhum evento selecionado"
        }

        // --- ADD THESE OBSERVERS FOR SECTORS ---
        viewModel.isLoadingSetores.observe(this) { isLoading ->
            // Assuming you have a ProgressBar in your layout with id 'progressBarSetores'
            // binding.progressBarSetores.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Example: Log.d("ScannerActivity", "isLoadingSetores: $isLoading")
        }

        viewModel.setores.observe(this) { setoresList ->
            // Assuming you have a Spinner in your layout with id 'spinnerSetores'
            // if (setoresList.isNotEmpty()) {
            //     val sectorNames = setoresList.map { it.nome } // Or a more descriptive string
            //     val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sectorNames)
            //     adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            //     // binding.spinnerSetores.adapter = adapter
            //     // Log.d("ScannerActivity", "Sectors loaded for spinner: ${sectorNames.joinToString()}")
            // } else {
            //     // Handle empty sector list, maybe disable spinner or show a message
            //     // Log.d("ScannerActivity", "Sector list is empty.")
            //     // Clear adapter if needed
            //     // binding.spinnerSetores.adapter = null
            // }
        }
        // --- END OF ADDED OBSERVERS FOR SECTORS ---
    }

    private fun setupListeners() {
        binding.btnVoltar.setOnClickListener {
            finish()
        }

        // --- THIS IS THE MODIFIED PART ---
        binding.btnTorchlight.setOnClickListener {
            if (isTorchOn) {
                barcodeScannerView.setTorchOff()
                isTorchOn = false
            } else {
                barcodeScannerView.setTorchOn()
                isTorchOn = true
            }
        }
        // --- END OF MODIFIED PART ---
    }


    private fun playSuccessSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        vibrator.vibrate(100)
    }

    private fun playErrorSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 400)
        vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
    }

    private fun showSuccess(message: String) {
        binding.textResult.text = message
        binding.textResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun showError(message: String) {
        binding.textResult.text = message
        binding.textResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeScanner()
            } else {
                // Handle permission denial gracefully, e.g., show a message and finish
                Toast.makeText(this, "Permissão da câmera negada.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::capture.isInitialized) {
            capture.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::capture.isInitialized) {
            capture.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::capture.isInitialized) {
            capture.onDestroy()
        }
        if (::toneGenerator.isInitialized) {
            toneGenerator.release()
        }
    }
}