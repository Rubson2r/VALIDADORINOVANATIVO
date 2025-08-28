package com.inovatickets.validador.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.inovatickets.validador.databinding.ActivityMainBinding
import com.inovatickets.validador.ui.viewmodels.MainViewModel
import com.inovatickets.validador.ui.viewmodels.MainViewModelFactory
import com.inovatickets.validador.ui.fragments.SincronizacaoFragment
import com.inovatickets.validador.ui.fragments.ValidacaoFragment
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.remote.EventoRemoteDataSource
import com.inovatickets.validador.data.repository.EventoRepository
import com.inovatickets.validador.data.repository.SyncRepository

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var eventoRemoteDataSource: EventoRemoteDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViewModel()
        setupTabs()
        setupObservers()
    }

    private fun initViewModel() {
        val database = AppDatabase.getDatabase(this)

        var supabaseUrl = "https://fqhbkqslawwpemwguhtw.supabase.co"
        val supabaseKey =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZxaGJrcXNsYXd3cGVtd2d1aHR3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM4NTA3ODEsImV4cCI6MjA2OTQyNjc4MX0.fWk-V5LKHsMrDL9Af0WboP6W-VjOb9pl0WRH1SgBMbA"
        if (!supabaseUrl.endsWith("/")) supabaseUrl += "/"

        eventoRemoteDataSource = EventoRemoteDataSource(supabaseUrl, supabaseKey)
        val syncRepository = SyncRepository(eventoRemoteDataSource)
        val eventoRepository = EventoRepository(database, eventoRemoteDataSource, syncRepository)

        val factory = MainViewModelFactory(eventoRepository, syncRepository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        sharedPrefs = getSharedPreferences("validador_prefs", Context.MODE_PRIVATE)
    }

    private fun setupTabs() {
        val adapter = TabsAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "SINCRONIZAÇÃO"
                1 -> "VALIDAÇÃO"
                else -> ""
            }
        }.attach()

        // Intercepta clique na aba "VALIDAÇÃO" para exigir seleção no campo (como o botão)
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                if (tab.position == 1) {
                    val ok = (getFragment(0) as? SincronizacaoFragment)
                        ?.tryStartValidacaoViaTab(navigate = false) == true
                    if (!ok) {
                        // Volta para SINCRONIZAÇÃO e alerta
                        binding.tabLayout.post {
                            binding.tabLayout.getTabAt(0)?.select()
                            Toast.makeText(
                                this@MainActivity,
                                "Selecione um evento em SINCRONIZAÇÃO antes de validar.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupObservers() {
        viewModel.eventosAtivos.observe(this) { eventos ->
            (getCurrentFragment() as? SincronizacaoFragment)?.updateEventos(eventos)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            (getCurrentFragment() as? SincronizacaoFragment)?.updateLoadingState(isLoading)
        }

        viewModel.error.observe(this) { errorMsg ->
            errorMsg?.let { Toast.makeText(this, "Erro: $it", Toast.LENGTH_LONG).show() }
        }

        viewModel.syncStatus.observe(this) { status ->
            (getCurrentFragment() as? SincronizacaoFragment)?.updateSyncStatus(status)
        }
    }

    private fun getCurrentFragment(): Fragment? {
        val adapter = binding.viewPager.adapter as? TabsAdapter
        return adapter?.getCurrentFragment(binding.viewPager.currentItem)
    }

    // Exposto pros fragments
    fun getMainViewModel(): MainViewModel = viewModel
    fun getSharedPrefs(): SharedPreferences = sharedPrefs
    fun getBinding() = binding
    fun getEventoRemoteDataSource() = eventoRemoteDataSource

    /** Chame isso depois de salvar `evento_ativo_id` via botão Iniciar Validação */
    fun notifyEventoSelecionado() {
        binding.viewPager.post {
            binding.viewPager.currentItem = 1
            binding.tabLayout.getTabAt(1)?.select()
        }
    }

    private class TabsAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        private val fragments = mutableMapOf<Int, Fragment>()
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            val fragment = when (position) {
                0 -> SincronizacaoFragment()
                1 -> ValidacaoFragment()
                else -> SincronizacaoFragment()
            }
            fragments[position] = fragment
            return fragment
        }
        fun getCurrentFragment(position: Int): Fragment? = fragments[position]
    }

    private fun getFragment(position: Int): Fragment? {
        val adapter = binding.viewPager.adapter as? TabsAdapter ?: return null
        return adapter.getCurrentFragment(position)
    }
}
