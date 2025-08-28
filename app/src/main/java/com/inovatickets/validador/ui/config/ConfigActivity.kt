package com.inovatickets.validador.ui.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.inovatickets.validador.R
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.remote.EventoRemoteDataSource // Added import
import com.inovatickets.validador.data.repository.EventoRepository
import com.inovatickets.validador.data.repository.SyncRepository
import com.inovatickets.validador.ui.viewmodels.MainViewModel
import com.inovatickets.validador.ui.viewmodels.MainViewModelFactory

class ConfigActivity : AppCompatActivity() {
    
    private lateinit var viewModel: MainViewModel
    private lateinit var sharedPrefs: SharedPreferences
    
    private lateinit var etNomeLeitor: EditText
    private lateinit var spEventos: Spinner
    private lateinit var btnSalvar: Button
    private lateinit var btnVoltar: Button
    private lateinit var switchOfflineMode: Switch
    private lateinit var tvVersao: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        
        initViews()
        initViewModel()
        setupListeners()
        loadConfiguration()
    }
    
    private fun initViews() {
        etNomeLeitor = findViewById(R.id.etNomeLeitor)
        spEventos = findViewById(R.id.spEventos)
        btnSalvar = findViewById(R.id.btnSalvar)
        btnVoltar = findViewById(R.id.btnVoltar)
        switchOfflineMode = findViewById(R.id.switchOfflineMode)
        tvVersao = findViewById(R.id.tvVersao)
        
        // Mostrar versão do app
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        tvVersao.text = "Versão: ${packageInfo.versionName}"
    }
    
    private fun initViewModel() {
        val database = AppDatabase.getDatabase(this)

        // --- Start of changes ---
        var supabaseUrl = "https://fqhbkqslawwpemwguhtw.supabase.co" // Your Supabase URL
        val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZxaGJrcXNsYXd3cGVtd2d1aHR3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM4NTA3ODEsImV4cCI6MjA2OTQyNjc4MX0.fWk-V5LKHsMrDL9Af0WboP6W-VjOb9pl0WRH1SgBMbA" // Your Supabase Key

        if (!supabaseUrl.endsWith("/")) {
            supabaseUrl += "/"
        }

        val eventoRemoteDataSource = EventoRemoteDataSource(supabaseUrl, supabaseKey)
        val syncRepository = SyncRepository(eventoRemoteDataSource) // Pass remoteDataSource
        val eventoRepository = EventoRepository(database, eventoRemoteDataSource, syncRepository) // Pass remoteDataSource and syncRepository
        // --- End of changes ---

        val factory = MainViewModelFactory(eventoRepository, syncRepository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
        
        sharedPrefs = getSharedPreferences("validador_prefs", Context.MODE_PRIVATE)
    }
    
    private fun setupListeners() {
        btnSalvar.setOnClickListener {
            saveConfiguration()
        }
        
        btnVoltar.setOnClickListener {
            finish()
        }
        
        // Observar lista de eventos
        viewModel.eventosAtivos.observe(this) { eventosAtivos ->
            val eventosArray = eventosAtivos.map { "${it.nome} (${it.data ?: ""})" }.toTypedArray()
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, eventosArray)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spEventos.adapter = adapter
            
            // Selecionar evento ativo atual
            val eventoAtivoId = sharedPrefs.getString("evento_ativo_id", null)
            if (eventoAtivoId != null) {
                val index = eventosAtivos.indexOfFirst { it.id == eventoAtivoId }
                if (index >= 0) {
                    spEventos.setSelection(index)
                }
            }
        }
    }
    
    private fun loadConfiguration() {
        // Carregar configurações salvas
        val nomeLeitor = sharedPrefs.getString("nome_leitor", "") ?: ""
        val offlineMode = sharedPrefs.getBoolean("offline_mode", false)
        
        etNomeLeitor.setText(nomeLeitor)
        switchOfflineMode.isChecked = offlineMode
        
        // Carregar eventos - não precisa chamar carregarEventos()
        // Os eventos serão carregados automaticamente pelo LiveData
    }
    
    private fun saveConfiguration() {
        val nomeLeitor = etNomeLeitor.text.toString().trim()
        
        if (nomeLeitor.isEmpty()) {
            Toast.makeText(this, "Digite o nome do leitor", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Salvar configurações
        val editor = sharedPrefs.edit()
        editor.putString("nome_leitor", nomeLeitor)
        editor.putBoolean("offline_mode", switchOfflineMode.isChecked)
        
        // Salvar evento selecionado
        if (spEventos.selectedItemPosition >= 0 && viewModel.eventosAtivos.value != null) {
            val eventoSelecionado = viewModel.eventosAtivos.value!![spEventos.selectedItemPosition]
            editor.putString("evento_ativo_id", eventoSelecionado.id)
            
            // Ativar evento no ViewModel
            viewModel.ativarEvento(eventoSelecionado.id)
        }
        
        editor.commit()
        
        Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show()
        finish()
    }
}