package com.inovatickets.validador.update

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.inovatickets.validador.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Lógica de update que funciona com repositório PRIVADO do GitHub.
 * (Baixa update.json e o APK via API com token.)
 */
object UpdateManager {

    // --- modelos auxiliares para a API do GitHub ---
    private data class GhAsset(
        val id: Long?,
        val name: String?,
        val url: String?,                    // URL da API do asset (precisa Accept: application/octet-stream)
        val browser_download_url: String?
    )
    private data class GhRelease(val assets: List<GhAsset>?)
    // ------------------------------------------------

    // OkHttp com headers para GitHub
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder()
                    .header("User-Agent", "ValidadorInova/${BuildConfig.VERSION_NAME}")
                if (BuildConfig.GITHUB_TOKEN.isNotEmpty() &&
                    (req.url.host == "github.com" ||
                            req.url.host == "api.github.com" ||
                            req.url.host.endsWith("githubusercontent.com"))
                ) {
                    b.header("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN}")
                }
                chain.proceed(b.build())
            }
            .build()
    }

    /**
     * Busca o update.json:
     * - RECOMENDADO p/ privado: UPDATE_JSON_URL = "https://api.github.com/repos/{repo}/releases/latest"
     *   -> pega release latest -> acha asset "update.json" -> baixa com Accept: application/octet-stream.
     * - Fallback: baixa direto se for público.
     */
    suspend fun fetchUpdateInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = BuildConfig.UPDATE_JSON_URL

        if (url.contains("api.github.com") && url.endsWith("/releases/latest")) {
            // 1) release latest
            val releaseReq = Request.Builder().url(url).get().build()
            client.newCall(releaseReq).execute().use { relResp ->
                if (!relResp.isSuccessful) return@withContext null
                val relBody = relResp.body?.string() ?: return@withContext null
                val release = try { Gson().fromJson(relBody, GhRelease::class.java) } catch (_: Exception) { null }
                    ?: return@withContext null

                // 2) asset update.json
                val asset = release.assets?.firstOrNull { it.name == "update.json" } ?: return@withContext null
                val assetApiUrl = asset.url ?: return@withContext null

                // 3) baixa com Accept: application/octet-stream
                val assetReq = Request.Builder()
                    .url(assetApiUrl)
                    .get()
                    .header("Accept", "application/octet-stream")
                    .build()

                client.newCall(assetReq).execute().use { aResp ->
                    if (!aResp.isSuccessful) return@withContext null
                    val json = aResp.body?.string() ?: return@withContext null
                    return@withContext try { Gson().fromJson(json, UpdateInfo::class.java) } catch (_: Exception) { null }
                }
            }
        }

        // Fallback público
        val directReq = Request.Builder().url(url).get().build()
        client.newCall(directReq).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            return@withContext try { Gson().fromJson(body, UpdateInfo::class.java) } catch (_: Exception) { null }
        }
    }

    fun isNewer(remoteCode: Int): Boolean = remoteCode > BuildConfig.VERSION_CODE

    /**
     * Faz o download do APK.
     * - PRIVADO: ignora download_url e usa API de assets via info.repo + info.release_tag + info.artifactName.
     * - PÚBLICO: usa download_url se existir.
     */
    suspend fun downloadApk(context: Context, info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        // 1) Tenta via API (privado)
        val apiUrl = findAssetApiUrl(info)
        if (apiUrl != null) {
            val req = Request.Builder()
                .url(apiUrl)
                .get()
                .header("Accept", "application/octet-stream")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val outFile = writeBodyToCache(context, resp, info)
                if (outFile != null && info.sha256.isNotBlank()) {
                    if (!sha256(outFile).equals(info.sha256, ignoreCase = true)) {
                        outFile.delete(); return@withContext null
                    }
                }
                return@withContext outFile
            }
        }

        // 2) Fallback público
        if (info.download_url.isNotBlank()) {
            val req = Request.Builder().url(info.download_url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val outFile = writeBodyToCache(context, resp, info)
                if (outFile != null && info.sha256.isNotBlank()) {
                    if (!sha256(outFile).equals(info.sha256, ignoreCase = true)) {
                        outFile.delete(); return@withContext null
                    }
                }
                return@withContext outFile
            }
        }

        return@withContext null
    }

    private fun writeBodyToCache(context: Context, resp: okhttp3.Response, info: UpdateInfo): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outFile = File(dir, info.artifactName.ifBlank { "update.apk" })
        resp.body?.byteStream()?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        return outFile
    }

    private suspend fun findAssetApiUrl(info: UpdateInfo): String? = withContext(Dispatchers.IO) {
        val repo = info.repo?.takeIf { it.contains("/") } ?: return@withContext null
        val tag = info.release_tag?.takeIf { it.isNotBlank() } ?: "latest"

        val releaseUrl = if (tag == "latest")
            "https://api.github.com/repos/$repo/releases/latest"
        else
            "https://api.github.com/repos/$repo/releases/tags/$tag"

        val releaseReq = Request.Builder().url(releaseUrl).get().build()
        client.newCall(releaseReq).execute().use { relResp ->
            if (!relResp.isSuccessful) return@withContext null
            val body = relResp.body?.string() ?: return@withContext null
            val release = try { Gson().fromJson(body, GhRelease::class.java) } catch (_: Exception) { null }
                ?: return@withContext null

            val asset = release.assets?.firstOrNull { it.name == info.artifactName } ?: return@withContext null
            return@withContext asset.url
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // Fluxo completo: checa, pergunta e instala
    suspend fun checkAndMaybeUpdate(activity: FragmentActivity) {
        val info = fetchUpdateInfo() ?: return
        if (!isNewer(info.versionCode)) return

        withContext(Dispatchers.Main) {
            AlertDialog.Builder(activity)
                .setTitle("Atualização disponível")
                .setMessage("Versão ${info.versionName} disponível. Deseja baixar e instalar agora?")
                .setNegativeButton("Depois", null)
                .setPositiveButton("Atualizar") { _, _ ->
                    // usa lifecycleScope da Activity (precisa lifecycle-runtime-ktx)
                    activity.lifecycleScope.launch {
                        val file = downloadApk(activity, info)
                        if (file == null) {
                            android.widget.Toast
                                .makeText(activity, "Falha ao baixar atualização", android.widget.Toast.LENGTH_LONG)
                                .show()
                            return@launch
                        }
                        val ok = UpdateInstaller.installApk(activity, file)
                        if (!ok) {
                            android.widget.Toast
                                .makeText(activity, "Conceda permissão para instalar apps desconhecidos e tente novamente.", android.widget.Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
                .show()
        }
    }
}
