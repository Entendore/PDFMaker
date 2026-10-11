package com.example.pdfmaker
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FillMemory {
    data class SavedAnn(val type: Int, val nx: Float, val ny: Float, val text: String,
        val sizePts: Float, val widthFrac: Float, val points: List<List<Float>>, val color: Int)
    data class SavedFill(val key: String, val name: String, val uri: String, val date: Long, val pages: Map<Int, List<SavedAnn>>) {
        fun entryCount() = pages.values.sumOf { it.size } }

    private const val PREFS = "fill_memory"; private const val KEY_FILLS = "fills"; private const val KEY_SIG = "signature_png"
    fun keyOf(displayName: String, sizeBytes: Long, pageCount: Int) = "$displayName|$sizeBytes|$pageCount"
    fun find(c: Context, key: String) = all(c)[key]
    fun list(c: Context) = all(c).values.sortedByDescending { it.date }
    fun save(c: Context, key: String, name: String, uri: String, pages: Map<Int, List<SavedAnn>>) {
        val root = JSONObject(prefs(c).getString(KEY_FILLS, "{}") ?: "{}")
        val o = JSONObject().put("name", name).put("uri", uri).put("date", System.currentTimeMillis())
        val po = JSONObject()
        pages.forEach { (pg, list) -> val arr = JSONArray()
            list.forEach { a -> val pts = JSONArray(); a.points.forEach { pts.put(JSONArray().put(it[0]).put(it[1])) }
                arr.put(JSONObject().put("t",a.type).put("x",a.nx.toDouble()).put("y",a.ny.toDouble())
                    .put("s",a.sizePts.toDouble()).put("w",a.widthFrac.toDouble()).put("tx",a.text).put("c",a.color).put("pts",pts)) }
            po.put(pg.toString(), arr) }
        o.put("pages", po); root.put(key, o)
        prefs(c).edit().putString(KEY_FILLS, root.toString()).apply() }
    fun delete(c: Context, key: String) {
        val root = JSONObject(prefs(c).getString(KEY_FILLS, "{}") ?: "{}"); root.remove(key)
        prefs(c).edit().putString(KEY_FILLS, root.toString()).apply() }
    private fun all(c: Context): Map<String, SavedFill> { val out = mutableMapOf<String, SavedFill>(); runCatching {
        val root = JSONObject(prefs(c).getString(KEY_FILLS, "{}") ?: "{}")
        root.keys().forEach { key -> val o = root.getJSONObject(key); val pages = mutableMapOf<Int, List<SavedAnn>>()
            val po = o.getJSONObject("pages")
            po.keys().forEach { pg -> val arr = po.getJSONArray(pg); val list = mutableListOf<SavedAnn>()
                for (i in 0 until arr.length()) { val a = arr.getJSONObject(i); val pts = mutableListOf<List<Float>>()
                    val pa = a.optJSONArray("pts") ?: JSONArray()
                    for (j in 0 until pa.length()) { val p = pa.getJSONArray(j)
                        pts.add(listOf(p.optDouble(0).toFloat(), p.optDouble(1).toFloat())) }
                    list.add(SavedAnn(a.optInt("t"), a.optDouble("x").toFloat(), a.optDouble("y").toFloat(),
                        a.optString("tx"), a.optDouble("s").toFloat(), a.optDouble("w").toFloat(), pts, a.optInt("c", Color.BLACK))) }
                pages[pg.toInt()] = list }
            out[key] = SavedFill(key, o.optString("name"), o.optString("uri"), o.optLong("date"), pages) } }
        return out }
    fun saveSignature(c: Context, bmp: Bitmap) { runCatching {
        val bytes = ByteArrayOutputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o); o.toByteArray() }
        prefs(c).edit().putString(KEY_SIG, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply() } }
    fun loadSignature(c: Context): Bitmap? = runCatching {
        prefs(c).getString(KEY_SIG, null)?.let { val d = Base64.decode(it, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(d, 0, d.size) } }.getOrNull()
    fun dateLabel(ms: Long) = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(ms))
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
