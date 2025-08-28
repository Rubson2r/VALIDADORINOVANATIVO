package com.inovatickets.validador.ui.fragments

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.inovatickets.validador.R
import com.inovatickets.validador.data.database.AppDatabase
import com.inovatickets.validador.data.database.dao.CodigoDao
import com.inovatickets.validador.data.database.dao.ConfiguracaoDao
import com.inovatickets.validador.data.database.dao.SetorDao
import com.inovatickets.validador.data.database.entities.Codigo
import com.inovatickets.validador.data.database.entities.ConfigKeys
import com.inovatickets.validador.data.database.entities.Configuracao
import com.inovatickets.validador.data.database.entities.Setor
import com.inovatickets.validador.data.repository.SyncRepository
import com.inovatickets.validador.databinding.FragmentValidacaoBinding
import com.inovatickets.validador.ui.MainActivity
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ValidacaoFragment : Fragment() {

    private var _binding: FragmentValidacaoBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainActivity: MainActivity
    private lateinit var syncRepository: SyncRepository
    private lateinit var setorDao: SetorDao
    private lateinit var codigoDao: CodigoDao
    private lateinit var configuracaoDao: ConfiguracaoDao

    private var contadorValidacoes = 0
    private var modoValidacaoAtivo = false
    private var modoSelecionado: String = ""
    private val setoresSelecionadosIds = mutableSetOf<String>()

    // ZXing
    private lateinit var barcodeView: DecoratedBarcodeView
    private var bloqueado = false
    private var resetJob: Job? = null

    // ==== Proteção de edição do nome do aparelho ====
    private var nomeLeitorPersistido: String = ""
    private var editMode: Boolean = false
    /** Troque essa senha ou mova para configs seguras; é apenas exemplo. */
    private val EDIT_PASSWORD = "2R@2R"

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScanner() else setStatusIdle("Permissão negada", "Autorize a câmera para validar")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentValidacaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity = requireActivity() as MainActivity
        syncRepository = SyncRepository(mainActivity.getEventoRemoteDataSource())

        val db = AppDatabase.getDatabase(requireContext())
        setorDao = db.setorDao()
        codigoDao = db.codigoDao()
        configuracaoDao = db.configuracaoDao()

        barcodeView = binding.decoratedBarcodeView

        setupListeners()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            ensureEventoAtivoResolvido()
            loadSetores()
            carregarNomeLeitor()           // <- carrega do DB sempre que voltar
            atualizarContadorValidadosEventoEAparelho()
            if (modoValidacaoAtivo && modoSelecionado == "camera") barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (modoSelecionado == "camera") stopScanner()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopScanner()
        _binding = null
    }

    private fun setupListeners() {
        // Validar
        binding.btnValidar.setOnClickListener {
            lifecycleScope.launch {
                if (!ensureEventoAtivoResolvido()) {
                    binding.textInstrucao.visibility = View.VISIBLE
                    binding.textInstrucao.text = "Nenhum evento ativo. Sincronize os dados primeiro."
                    return@launch
                }
                iniciarValidacao()
            }
        }

        // Voltar
        binding.btnVoltar.setOnClickListener { voltarParaConfig() }
        binding.btnVoltarCamera?.setOnClickListener { voltarParaConfig() }

        // Modos
        binding.btnModoCamera.setOnClickListener { selecionarModoCamera() }
        binding.btnModoLeitor.setOnClickListener { selecionarModoLeitor() }

        // Leitor manual
        binding.etCodigoManual.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val raw = binding.etCodigoManual.text?.toString().orEmpty()
                dispararValidacaoManual(raw)
                true
            } else false
        }
        binding.etCodigoManual.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val raw = binding.etCodigoManual.text?.toString().orEmpty()
                dispararValidacaoManual(raw)
                true
            } else false
        }

        // === Edição protegida do NOME DO LEITOR ===
        binding.btnEditarNomeLeitor.setOnClickListener {
            if (editMode) {
                // Cancelar edição → volta ao bloqueado e restaura texto persistido
                setNomeLeitorEditable(false)
                binding.etNomeLeitor.setText(nomeLeitorPersistido)
            } else {
                pedirSenhaEDestravar()
            }
        }

        binding.btnSalvarNomeLeitor.setOnClickListener {
            salvarNomeLeitor()
        }
    }

    private fun pedirSenhaEDestravar() {
        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Senha"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Editar identificação")
            .setMessage("Informe a senha para editar o nome do leitor.")
            .setView(input)
            .setPositiveButton("OK") { dialog: DialogInterface, _ ->
                val senha = input.text?.toString() ?: ""
                if (senha == EDIT_PASSWORD) {
                    setNomeLeitorEditable(true)
                } else {
                    Toast.makeText(requireContext(), "Senha inválida", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun setNomeLeitorEditable(enabled: Boolean) {
        editMode = enabled
        binding.etNomeLeitor.isEnabled = enabled
        binding.etNomeLeitor.isFocusable = enabled
        binding.etNomeLeitor.isFocusableInTouchMode = enabled
        binding.etNomeLeitor.isClickable = enabled

        binding.btnSalvarNomeLeitor.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.btnEditarNomeLeitor.text = if (enabled) "Cancelar" else "Editar"

        if (enabled) binding.etNomeLeitor.requestFocus()
    }

    private fun salvarNomeLeitor() {
        val novo = binding.etNomeLeitor.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (novo.isEmpty()) {
            Toast.makeText(requireContext(), "Informe um nome válido", Toast.LENGTH_SHORT).show()
            return
        }

        // Persiste no Room (tabela configuracoes)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                configuracaoDao.insertConfiguracao(
                    Configuracao(
                        chave = ConfigKeys.APARELHO_NOME, // <- ADICIONAR essa chave em ConfigKeys
                        valor = novo,
                        descricao = "Identificação do aparelho/leitor"
                    )
                )
                nomeLeitorPersistido = novo
                withContext(Dispatchers.Main) {
                    setNomeLeitorEditable(false)
                    binding.etNomeLeitor.setText(novo)
                    Toast.makeText(requireContext(), "Nome do leitor salvo", Toast.LENGTH_SHORT).show()
                    atualizarContadorValidadosEventoEAparelho()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Falha ao salvar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun carregarNomeLeitor() {
        lifecycleScope.launch(Dispatchers.IO) {
            val salvo = try { configuracaoDao.getValor(ConfigKeys.APARELHO_NOME) } catch (_: Exception) { null }
            val valor = if (!salvo.isNullOrBlank()) salvo
            else "LEITOR-" + android.os.Build.MODEL.replace(" ", "").take(6).uppercase(Locale.ROOT)

            nomeLeitorPersistido = valor
            withContext(Dispatchers.Main) {
                binding.etNomeLeitor.setText(valor)
                setNomeLeitorEditable(false) // garante bloqueado
            }
        }
    }

    private fun dispararValidacaoManual(raw: String) {
        val codigo = raw.trim().replace("\n", "")
        if (codigo.isEmpty()) return
        if (bloqueado) return
        bloqueado = true
        esconderTeclado()
        lifecycleScope.launch {
            processarCodigo(codigo)
            binding.etCodigoManual.setText("")
            binding.etCodigoManual.requestFocus()
        }
    }

    private fun esconderTeclado() {
        try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etCodigoManual.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun setupUI() {
        // Nome será carregado do DB em onResume (carregarNomeLeitor)
        binding.textContadorValidacoes.text = "0"
        binding.textEventoAtual.visibility = View.GONE
        binding.textEventoAtual.text = ""
    }

    // ---------- evento ativo ----------
    private fun getEventoIdFromPrefs(): String? {
        val sp = requireActivity().getSharedPreferences("validador_prefs", Context.MODE_PRIVATE)
        return sp.getString("evento_ativo_id", null)
    }
    private fun setEventoIdInPrefs(id: String) {
        val sp = requireActivity().getSharedPreferences("validador_prefs", Context.MODE_PRIVATE)
        sp.edit().putString("evento_ativo_id", id).apply()
    }
    private suspend fun ensureEventoAtivoResolvido(): Boolean = withContext(Dispatchers.IO) {
        getEventoIdFromPrefs()?.let { return@withContext true }
        try {
            val cfgId = configuracaoDao.getValor(ConfigKeys.EVENTO_ATIVO)
            if (!cfgId.isNullOrBlank()) {
                withContext(Dispatchers.Main) { setEventoIdInPrefs(cfgId) }
                return@withContext true
            }
        } catch (_: Exception) {}
        return@withContext false
    }
    private fun getEventoId(): String? = getEventoIdFromPrefs()

    // ---------- carregar setores ----------
    private fun loadSetores() {
        lifecycleScope.launch {
            val eventoId = getEventoId()
            Log.d("ValidacaoFragment", "loadSetores: eventoId=$eventoId")
            if (eventoId.isNullOrBlank()) {
                setupSetores(emptyList())
                return@launch
            }
            val setoresLocal: List<Setor> = withContext(Dispatchers.IO) { setorDao.getByEventoId(eventoId) }
            setupSetores(setoresLocal)
        }
    }

    private fun setupSetores(setores: List<Setor>) {
        binding.setoresContainer.removeAllViews()
        for (setor in setores) {
            val setorId = try {
                val f = Setor::class.java.getDeclaredField("id"); f.isAccessible = true; f.get(setor) as String
            } catch (_: Exception) { continue }

            val setorName = try {
                val f = Setor::class.java.getDeclaredField("nome"); f.isAccessible = true; (f.get(setor) as? String) ?: "SEM NOME"
            } catch (_: Exception) {
                try { val f = Setor::class.java.getDeclaredField("nomeSetor"); f.isAccessible = true; (f.get(setor) as? String) ?: "SEM NOME" }
                catch (_: Exception) { "SEM NOME" }
            }

            val item = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 12, 12, 12)
                background = resources.getDrawable(R.drawable.edit_text_background, null)
                val mb = (8 * resources.displayMetrics.density).toInt()
                (layoutParams as ViewGroup.MarginLayoutParams).setMargins(0, 0, 0, mb)
            }

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = setorName
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
            }

            val sw = Switch(requireContext()).apply {
                isChecked = setoresSelecionadosIds.contains(setorId)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) setoresSelecionadosIds.add(setorId) else setoresSelecionadosIds.remove(setorId)
                    verificarSetoresSelecionados()
                }
            }

            item.addView(label)
            item.addView(sw)
            binding.setoresContainer.addView(item)
        }
        verificarSetoresSelecionados()
    }

    // ---------- modos ----------
    private fun selecionarModoCamera() {
        modoSelecionado = "camera"
        binding.btnModoCamera.setBackgroundResource(R.drawable.button_primary)
        binding.btnModoLeitor.setBackgroundResource(R.drawable.button_secondary)
        binding.btnModoCamera.setTextColor(resources.getColor(R.color.white, null))
        binding.btnModoLeitor.setTextColor(resources.getColor(R.color.text_primary, null))
        binding.btnValidar.isEnabled = true
        binding.textInstrucao.visibility = View.GONE
    }

    private fun selecionarModoLeitor() {
        modoSelecionado = "leitor"
        binding.btnModoLeitor.setBackgroundResource(R.drawable.button_primary)
        binding.btnModoCamera.setBackgroundResource(R.drawable.button_secondary)
        binding.btnModoCamera.setTextColor(resources.getColor(R.color.text_primary, null))
        binding.btnModoLeitor.setTextColor(resources.getColor(R.color.white, null))
        binding.btnValidar.isEnabled = true
        binding.textInstrucao.visibility = View.GONE
    }

    private fun iniciarValidacao() {
        modoValidacaoAtivo = true
        binding.layoutConfig.visibility = View.GONE
        binding.layoutValidacao.visibility = View.VISIBLE

        showIdleUI()

        if (modoSelecionado == "camera") {
            binding.cardCamera.visibility = View.VISIBLE
            binding.layoutLeitorManual.visibility = View.GONE
            iniciarCamera()
            setStatusIdle("CÂMERA ATIVA", "Aponte para o QR Code")
        } else {
            stopScanner()
            binding.cardCamera.visibility = View.GONE
            binding.layoutLeitorManual.visibility = View.VISIBLE
            setStatusIdle("LEITOR ATIVO", "Aguardando leitura do dispositivo")
            binding.etCodigoManual.setText("")
            binding.etCodigoManual.requestFocus()
        }

        atualizarContadorValidadosEventoEAparelho()
        atualizarContadorVisual()
    }

    // ---------- ZXing ----------
    private fun iniciarCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) cameraPermissionLauncher.launch(Manifest.permission.CAMERA) else startScanner()
    }

    private fun startScanner() {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                val lido = result.text ?: return
                if (bloqueado) return
                bloqueado = true
                lifecycleScope.launch { processarCodigo(lido) }
            }
        })
        barcodeView.resume()
        setStatusIdle("CÂMERA ATIVA", "Aponte para o QR Code")
    }

    private fun stopScanner() {
        try { barcodeView.pause() } catch (_: Throwable) {}
    }

    // ---------- validação ----------
    private suspend fun processarCodigo(valor: String) {
        val codigoLido = valor.trim()

        val hasEvento = ensureEventoAtivoResolvido()
        val eventoId = getEventoId()
        if (!hasEvento || eventoId.isNullOrBlank()) {
            setStatusIdle("Sem evento", "Sincronize ou selecione um evento e tente novamente")
            liberarDepois()
            return
        }

        val registro: Codigo? = withContext(Dispatchers.IO) { codigoDao.findByCodigoAndEvento(codigoLido, eventoId) }

        if (registro == null) {
            mostrarResultadoInvalido(null)
//            setStatusIdle("CÓDIGO INVÁLIDO", "Tente novamente")
            liberarDepois()
            return
        }

        if (!setoresSelecionadosIds.contains(registro.setor_id)) {
//            setStatusIdle("SETOR NÃO PERMITIDO", "Selecione o setor correto")
            mostrarResultadoInvalido(setor = true)
            liberarDepois()
            return
        }

        if (registro.utilizado) {
            mostrarResultadoJaUtilizado(registro.timestamp_utilizacao)
            liberarDepois()
            return
        }

        val agora = Date()
        val usuario = binding.etNomeLeitor.text?.toString()
        withContext(Dispatchers.IO) {
            codigoDao.marcarUtilizado(registro.id, agora, usuario)
        }

        atualizarContadorValidadosEventoEAparelho()
        mostrarResultadoLiberado()
        liberarDepois()
    }

    private fun liberarDepois(timeoutMs: Long = 2000) {
        resetJob?.cancel()
        resetJob = lifecycleScope.launch {
            delay(timeoutMs)
            showIdleUI()
            bloqueado = false
            if (modoSelecionado == "camera") {
                try { barcodeView.resume() } catch (_: Throwable) {}
            } else {
                binding.etCodigoManual.requestFocus()
            }
        }
    }

    // ---------- estados de UI ----------
    private fun showIdleUI() {
        binding.cardStatus.visibility = View.VISIBLE
        binding.cardResultadoUsado.visibility = View.GONE
        binding.cardResultadoLiberado.visibility = View.GONE
        binding.cardResultadoInvalido.visibility = View.GONE
        setStatusIdle("AGUARDANDO LEITURA", "Posicione o código para validação")
    }

    private fun setStatusIdle(title: String, subtitle: String) {
        binding.textStatusTitle.text = title
        binding.textStatusSubtitle.text = subtitle
    }

    fun mostrarResultadoJaUtilizado(utilizadoEm: Date?) {
        stopScanner()
        binding.cardStatus.visibility = View.GONE
        binding.cardResultadoLiberado.visibility = View.GONE
        binding.cardResultadoInvalido.visibility = View.GONE
        binding.cardResultadoUsado.visibility = View.VISIBLE
        binding.textUsadoInfo.text = "Utilizado em: ${formatBr(utilizadoEm)}"
    }

    fun mostrarResultadoInvalido(setor: Boolean?) {
        stopScanner()
        binding.cardStatus.visibility = View.GONE
        binding.cardResultadoInvalido.visibility = View.VISIBLE
        binding.cardResultadoLiberado.visibility = View.GONE
        binding.cardResultadoUsado.visibility = View.GONE
        if (setor == true) {
            binding.textInvalidoTitulo.text = "SETOR NÃO PERMITIDO"
        } else {
            binding.textInvalidoTitulo.text = "CÓDIGO INVÁLIDO"
        }
    }

    fun mostrarResultadoLiberado() {
        stopScanner()
        binding.cardStatus.visibility = View.GONE
        binding.cardResultadoUsado.visibility = View.GONE
        binding.cardResultadoInvalido.visibility = View.GONE
        binding.cardResultadoLiberado.visibility = View.VISIBLE
    }

    // ---------- util ----------
    private fun atualizarContadorVisual() {
        binding.textContadorValidacoes.text = contadorValidacoes.toString()
        binding.textContadorInferior?.text = contadorValidacoes.toString()
    }

    private fun atualizarContadorValidadosEventoEAparelho() {
        lifecycleScope.launch {
            val eventoId = getEventoId() ?: run {
                binding.textContadorValidacoes.text = "0"
                binding.textContadorInferior?.text = "0"
                return@launch
            }
            val aparelho = binding.etNomeLeitor.text?.toString()?.trim().orEmpty()
            val qtd = withContext(Dispatchers.IO) { codigoDao.countValidadosPorEventoEAparelho(eventoId, aparelho) }
            binding.textContadorValidacoes.text = qtd.toString()
            binding.textContadorInferior?.text = qtd.toString()
        }
    }

    private fun verificarSetoresSelecionados() {
        val temSetores = setoresSelecionadosIds.isNotEmpty()
        binding.btnValidar.isEnabled = temSetores && modoSelecionado.isNotEmpty()
        binding.textInstrucao.visibility = if (temSetores) View.GONE else View.VISIBLE
        if (!temSetores) binding.textInstrucao.text = "Selecione pelo menos um setor para validar"
    }

    private fun voltarParaConfig() {
        modoValidacaoAtivo = false
        stopScanner()
        binding.layoutConfig.visibility = View.VISIBLE
        binding.layoutValidacao.visibility = View.GONE
        showIdleUI()
        bloqueado = false
        binding.etCodigoManual.setText("")
    }

    fun updateEventoAtual(nomeEvento: String) {
        if (!::mainActivity.isInitialized) return
        binding.textEventoAtual.text = nomeEvento.uppercase(Locale.ROOT)
        binding.textEventoAtual.visibility = View.VISIBLE
    }

    fun incrementarContador() {
        contadorValidacoes++
        binding.textContadorValidacoes.text = contadorValidacoes.toString()
        binding.textContadorInferior?.text = contadorValidacoes.toString()
    }

    fun resetarContador() {
        contadorValidacoes = 0
        binding.textContadorValidacoes.text = "0"
    }
}

// --- Utils de data BR ---
private val TZ_SP = TimeZone.getTimeZone("America/Sao_Paulo")
private val LOCALE_BR = Locale("pt", "BR")
private fun formatBr(date: Date?): String {
    if (date == null) return "--/--/----, --:--:--"
    val sdf = SimpleDateFormat("dd/MM/yyyy, HH:mm:ss", LOCALE_BR)
    sdf.timeZone = TZ_SP
    return sdf.format(date)
}
