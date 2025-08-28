package com.inovatickets.validador.ui.fragments

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.database.dao.CodigoDao
import com.inovatickets.validador.data.database.dao.ConfiguracaoDao
import com.inovatickets.validador.data.database.entities.Evento
import com.inovatickets.validador.databinding.FragmentSincronizacaoBinding
import com.inovatickets.validador.ui.MainActivity
import com.inovatickets.validador.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SincronizacaoFragment : Fragment() {

    private var _binding: FragmentSincronizacaoBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainActivity: MainActivity
    private var eventosAtivos: List<Evento> = emptyList()

    // DAOsSs
    private lateinit var codigoDao: CodigoDao
    private lateinit var configuracaoDao: ConfiguracaoDao

    // Conectividade
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isOnline: Boolean = false
    private var isSyncing: Boolean = false

    // Update: evita checar múltiplas vezes por sessão
    private var updateChecked = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSincronizacaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity = requireActivity() as MainActivity

        val db = AppDatabase.getDatabase(requireContext())
        codigoDao = db.codigoDao()
        configuracaoDao = db.configuracaoDao()

        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // Estado inicial + registrar callback
        isOnline = checkOnline()
        applyConnectivityState()

        if (networkCallback == null) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    setOnlineState(checkOnline())
                }
                override fun onLost(network: Network) {
                    setOnlineState(checkOnline())
                }
                override fun onUnavailable() {
                    setOnlineState(false)
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    setOnlineState(online)
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        atualizarUIStatus()

        // Dispara checagem de atualização ao abrir se estiver online (somente uma vez por ciclo do fragment)
        if (isOnline && !updateChecked) {
            updateChecked = true
            viewLifecycleOwner.lifecycleScope.launch {
                UpdateManager.checkAndMaybeUpdate(requireActivity())
            }
        }
    }

    override fun onPause() {
        super.onPause()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
    }

    private fun setupListeners() {
        binding.autoCompleteEventos.setOnItemClickListener { _, _, position, _ ->
            val evento = (binding.autoCompleteEventos.adapter as ArrayAdapter<String>).getItem(position)
            binding.autoCompleteEventos.setText(evento ?: "", false)
        }

        binding.btnConectar.setOnClickListener {
            mainActivity.getMainViewModel().sincronizarDados()
        }

        binding.btnIniciarValidacao.setOnClickListener {
            if (!tryStartValidacaoViaTab(navigate = true)) {
                android.widget.Toast.makeText(requireContext(), "Selecione um evento primeiro", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Mostra "Última sincronização" e "Pendente de Sincronização" (globais) */
    private fun atualizarUIStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            val lastSyncEpoch: Long? = withContext(Dispatchers.IO) {
                try { configuracaoDao.getValor("LAST_SYNC_TS")?.toLongOrNull() } catch (_: Exception) { null }
            }
            if (!isAdded || _binding == null) return@launch
            binding.txUltimaSync.text = lastSyncEpoch?.let { formatBr(it) } ?: "Nunca"

            val pendentes: Int = withContext(Dispatchers.IO) {
                try { codigoDao.countValidacoesNaoSincronizadasGlobal() } catch (_: Exception) { 0 }
            }
            if (!isAdded || _binding == null) return@launch
            if (pendentes > 0) {
                binding.tvPendenteStatus.text = "Sim"
                binding.tvPendenteStatus.setTextColor(Color.parseColor("#EF4444"))
            } else {
                binding.tvPendenteStatus.text = "Não"
                binding.tvPendenteStatus.setTextColor(Color.parseColor("#16A34A"))
            }
        }
    }

    // === Conectividade ===
    private fun checkOnline(): Boolean {
        val nw = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(nw) ?: return false
        val hasNet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasNet && validated
    }

    private fun setOnlineState(newOnline: Boolean) {
        if (isOnline == newOnline) return
        isOnline = newOnline
        applyConnectivityState()

        // Se acabou de ficar online e ainda não checou update, faz agora
        if (isOnline && !updateChecked && isAdded) {
            updateChecked = true
            viewLifecycleOwner.lifecycleScope.launch {
                UpdateManager.checkAndMaybeUpdate(requireActivity())
            }
        }
    }

    /** Sempre posta alterações de UI para a Main Thread */
    private fun applyConnectivityState() {
        if (!isAdded || _binding == null) return
        binding.root.post {
            if (!isAdded || _binding == null) return@post
            binding.btnConectar.isEnabled = isOnline && !isSyncing
            binding.tvNoInternet.visibility = if (isOnline) View.GONE else View.VISIBLE
        }
    }

    // Chamado pelo ViewModel durante a sync
    fun updateLoadingState(isLoading: Boolean) {
        isSyncing = isLoading
        if (!isAdded || _binding == null) return
        binding.root.post {
            if (!isAdded || _binding == null) return@post
            binding.progressSync.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnConectar.text = if (isLoading) "SINCRONIZANDO..." else "SINCRONIZAR"
            applyConnectivityState()
            if (!isLoading) atualizarUIStatus()
        }
    }

    // Chamado pelo ViewModel para mensagens de status
    fun updateSyncStatus(status: String) {
        if (!isAdded || _binding == null) return
        binding.root.post {
            if (!isAdded || _binding == null) return@post
            binding.textSyncStatus.text = status
            if (status.contains("conclu", ignoreCase = true) ||
                status.contains("finaliz", ignoreCase = true)) {
                atualizarUIStatus()
            }
        }
    }

    fun updateEventos(eventos: List<Evento>) {
        eventosAtivos = eventos.filter { it.status == "ativo" }
        val nomes = eventosAtivos.map { it.nome }
        if (!isAdded || _binding == null) return
        binding.root.post {
            if (!isAdded || _binding == null) return@post
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nomes)
            binding.autoCompleteEventos.setAdapter(adapter)
            binding.btnIniciarValidacao.isEnabled = eventosAtivos.isNotEmpty()
        }
    }

    private fun formatBr(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy, HH:mm:ss", Locale("pt", "BR"))
        sdf.timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        return sdf.format(epochMillis)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Tenta iniciar validação usando o QUE ESTÁ NO CAMPO de eventos (não usa SharedPrefs antigos).
     * Se [navigate] = true, navega para a aba de validação ao sucesso (mesmo fluxo do botão).
     * Se [navigate] = false, só prepara o estado (usado ao clicar na aba "VALIDAÇÃO").
     */
    fun tryStartValidacaoViaTab(navigate: Boolean): Boolean {
        val nomeSelecionado = binding.autoCompleteEventos.text?.toString()?.trim()
        if (nomeSelecionado.isNullOrEmpty()) return false

        val evento = eventosAtivos.firstOrNull { it.nome == nomeSelecionado } ?: return false

        // salva o ID no SharedPreferences (commit síncrono)
        val sp = requireActivity().getSharedPreferences("validador_prefs", Context.MODE_PRIVATE)
        sp.edit().putString("evento_ativo_id", evento.id).commit()

        // atualiza header da validação
        val parent = requireActivity() as MainActivity
        val validacaoFragment = parent.supportFragmentManager.fragments
            .filterIsInstance<com.inovatickets.validador.ui.fragments.ValidacaoFragment>()
            .firstOrNull()
        validacaoFragment?.updateEventoAtual(evento.nome)

        if (navigate) parent.notifyEventoSelecionado()
        return true
    }
}
