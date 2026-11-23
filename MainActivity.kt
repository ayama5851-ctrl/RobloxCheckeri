package com.example.robloxchecker

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class Account(val username:String, val nickname:String, val ip:String)
data class ResultRow(
    val username:String, val nickname:String, val ip:String,
    val rap:String="UNKNOWN", val korblox:String="UNKNOWN",
    val headless:String="UNKNOWN", val bonafield:String="UNKNOWN",
    val effects:String="UNKNOWN", val age:String="UNKNOWN", val status:String="WAITING"
)

class MainActivity : Activity() {
    private val pool = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val rows = mutableListOf<ResultRow>()
    private val accounts = mutableListOf<Account>()
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resultsBox: LinearLayout
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private var delayMs = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Roblox Checker"
            textSize = 25f; setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF17233B.toInt())
        })
        root.addView(TextView(this).apply {
            text = "APK native • tanpa browser CORS • hanya data profil/inventory publik"
            textSize = 13f; setTextColor(0xFF58657A.toInt())
            setPadding(0, 4, 0, 18)
        })

        val importBtn = Button(this).apply { text = "PILIH FILE TXT" }
        root.addView(importBtn)
        importBtn.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
            }
            startActivityForResult(i, 100)
        }

        root.addView(TextView(this).apply {
            text = "Format: username:nickname:ip (satu akun per baris)"
            textSize = 12f; setTextColor(0xFF58657A.toInt())
            setPadding(0, 6, 0, 10)
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startBtn = Button(this).apply { text = "START" ; isEnabled = false }
        stopBtn = Button(this).apply { text = "STOP"; isEnabled = false }
        buttons.addView(startBtn, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stopBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)

        val exportBtn = Button(this).apply { text = "EXPORT CSV" ; isEnabled = false }
        root.addView(exportBtn)
        exportBtn.setOnClickListener { exportCsv() }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 14))
        status = TextView(this).apply {
            text = "Belum ada TXT."
            textSize = 13f; setTextColor(0xFF26344C.toInt())
            setPadding(0, 10, 0, 14)
        }
        root.addView(status)
        resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultsBox)

        startBtn.setOnClickListener { startChecking(exportBtn) }
        stopBtn.setOnClickListener { stopped.set(true); stopBtn.isEnabled = false }
    }

    @Deprecated("Deprecated in Android API but kept for broad device compatibility")
    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 101 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try { saveCsv(uri); Toast.makeText(this, "CSV berhasil disimpan", Toast.LENGTH_SHORT).show() }
            catch(e:Exception) { Toast.makeText(this, "Gagal menyimpan CSV: ${e.message}", Toast.LENGTH_LONG).show() }
            return
        }
        if(requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri).use { stream ->
                    val text = BufferedReader(InputStreamReader(stream!!)).readText()
                    accounts.clear()
                    text.lineSequence().forEach { line ->
                        val s = line.trim()
                        if(s.isNotEmpty() && !s.startsWith("#")) {
                            val p = s.split(":")
                            if(p[0].isNotBlank()) accounts.add(
                                Account(p[0].trim(), p.getOrNull(1)?.trim().orEmpty(),
                                    p.drop(2).joinToString(":").trim())
                            )
                        }
                    }
                }
                rows.clear(); resultsBox.removeAllViews()
                startBtn.isEnabled = accounts.isNotEmpty()
                status.text = "${accounts.size} akun terbaca."
            } catch(e:Exception) { status.text = "Gagal membaca TXT: ${e.message}" }
        }
    }

    private fun startChecking(exportBtn:Button) {
        if(accounts.isEmpty()) return
        stopped.set(false); startBtn.isEnabled = false; stopBtn.isEnabled = true
        exportBtn.isEnabled = false
        rows.clear(); resultsBox.removeAllViews(); progress.progress = 0
        pool.execute {
            var done = 0
            for (account in accounts) {
                if(stopped.get()) break
                val result = checkAccount(account)
                rows.add(result)
                done++
                runOnUiThread {
                    progress.progress = (done * 100 / accounts.size)
                    status.text = "$done / ${accounts.size} selesai • ${result.status}"
                    addResultCard(result)
                }
                if(!stopped.get()) try { Thread.sleep(delayMs) } catch(_:InterruptedException) {}
            }
            runOnUiThread {
                startBtn.isEnabled = true; stopBtn.isEnabled = false
                exportBtn.isEnabled = rows.isNotEmpty()
                status.text = "$done / ${accounts.size} selesai" + if(stopped.get()) " • dihentikan" else " • selesai"
            }
        }
    }

    private fun checkAccount(a:Account):ResultRow {
        var r = ResultRow(a.username,a.nickname,a.ip.ifBlank{"—"})
        try {
            val body = JSONObject().put("usernames", org.json.JSONArray().put(a.username))
                .put("excludeBannedUsers", false)
            val found = requestJson("https://users.roblox.com/v1/usernames/users", "POST", body.toString())
            val data = found.optJSONArray("data")
            if(data == null || data.length()==0)
                return r.copy(rap="—",korblox="—",headless="—",bonafield="—",effects="—",age="—",status="NOT FOUND")
            val user = data.getJSONObject(0)
            val id = user.getLong("id")

            var age = "UNKNOWN"
            try {
                val profile = requestJson("https://users.roblox.com/v1/users/$id")
                age = ageFrom(profile.optString("created"))
            } catch(_:Exception) {}

            try {
                val inv = requestJson("https://inventory.roblox.com/v1/users/$id/assets/collectibles?limit=100&sortOrder=Desc")
                val items = inv.optJSONArray("data") ?: org.json.JSONArray()
                var rap = 0L
                val effects = linkedSetOf<String>()
                var k = false; var h = false; var b = false
                for(i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    val low = name.lowercase()
                    rap += item.optLong("recentAveragePrice",0)
                    if(low.contains("korblox")) k = true
                    if(low.contains("headless")) h = true
                    if(low.contains("bonafield")) b = true
                    if(Regex("effect|aura|flame|fire|sparkle|particle|trail").containsMatchIn(low)) effects.add(name)
                }
                val partial = !inv.isNull("nextPageCursor") && inv.optString("nextPageCursor").isNotBlank()
                r = r.copy(rap=String.format("%,d",rap), korblox=if(k)"Yes" else "No",
                    headless=if(h)"Yes" else "No", bonafield=if(b)"Yes" else "No",
                    effects=effects.joinToString(", ").ifBlank{"No"}, age=age,
                    status=if(partial) "PARTIAL (halaman pertama)" else "OK")
            } catch(e:Exception) {
                val label = classify(e)
                r = r.copy(age=age,rap=label,korblox=label,headless=label,bonafield=label,effects=label,status=label)
            }
        } catch(e:Exception) {
            r = r.copy(status=classify(e))
        }
        return r
    }

    private fun requestJson(url:String, method:String="GET", body:String?=null):JSONObject {
        var last:Exception? = null
        for(attempt in 0..2) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection)
                conn.requestMethod = method
                conn.connectTimeout = 15000; conn.readTimeout = 20000
                conn.setRequestProperty("Accept","application/json")
                conn.setRequestProperty("User-Agent","RobloxCheckerAndroid/1.0")
                if(body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type","application/json")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if(code == 429 || code >= 500) {
                    conn.disconnect()
                    if(attempt < 2) { Thread.sleep(1200L*(attempt+1)); continue }
                    throw Exception("HTTP $code")
                }
                val stream = if(code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if(code !in 200..299) throw Exception("HTTP $code")
                return JSONObject(text)
            } catch(e:Exception) {
                last=e
                if(e.message?.startsWith("HTTP 4") == true && e.message != "HTTP 429") break
                if(attempt<2) Thread.sleep(700L*(attempt+1))
            }
        }
        throw last ?: Exception("NETWORK ERROR")
    }

    private fun classify(e:Exception):String {
        val m=e.message.orEmpty()
        return when {
            m.contains("HTTP 429") -> "RATE LIMITED"
            m.contains("HTTP 403") -> "ACCESS DENIED / PRIVATE"
            m.contains("HTTP 404") -> "NOT FOUND"
            m.contains("timeout",true) -> "TIMEOUT"
            else -> m.ifBlank{"NETWORK ERROR"}
        }
    }

    private fun ageFrom(created:String):String {
        return try {
            val d = java.time.Instant.parse(created).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val now = java.time.LocalDate.now()
            val p = java.time.Period.between(d,now)
            "${p.years} thn ${p.months} bln"
        } catch(_:Exception) { "UNKNOWN" }
    }

    private fun addResultCard(r:ResultRow) {
        val card = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(16,12,16,12)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val title = TextView(this).apply {
            text="${r.username}  •  ${r.status}"
            textSize=16f; setTypeface(null,Typeface.BOLD); setTextColor(0xFF17233B.toInt())
        }
        card.addView(title)
        val detail = TextView(this).apply {
            text="Nickname: ${r.nickname}\nIP TXT: ${r.ip}\nRAP: ${r.rap}\nKorblox: ${r.korblox}  |  Headless: ${r.headless}\nBonafield: ${r.bonafield}\nEffects: ${r.effects}\nUmur: ${r.age}"
            textSize=13f; setTextColor(0xFF26344C.toInt()); setPadding(0,8,0,8)
        }
        card.addView(detail)
        val copyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addCopyButton(label:String, value:String) {
            val copy = Button(this).apply { text=label }
            copy.setOnClickListener {
                val cb=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(this@MainActivity,"$label disalin",Toast.LENGTH_SHORT).show()
            }
            copyRow.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        }
        addCopyButton("COPY USER", r.username)
        addCopyButton("COPY NICK", r.nickname)
        addCopyButton("COPY IP", r.ip)
        card.addView(copyRow)
        val lp=LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,12)
        resultsBox.addView(card,lp)
    }

    private fun exportCsv() {
        val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="text/csv"; putExtra(Intent.EXTRA_TITLE,"roblox-checker.csv")
        }
        startActivityForResult(i,101)
    }

    private fun saveCsv(uri:Uri) {
        val keys="username,nickname,ip,rap,korblox,headless,bonafield,effects,age,status"
        val content=buildString {
            append(keys).append("\n")
            rows.forEach { r ->
                val vals=listOf(r.username,r.nickname,r.ip,r.rap,r.korblox,r.headless,r.bonafield,r.effects,r.age,r.status)
                append(vals.joinToString(",") { "\"" + it.replace("\"","\"\"") + "\"" }).append("\n")
            }
        }
        contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }
}
