package com.example.pdfmaker
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Template(val id: String, val name: String, val typeId: String,
    val fields: Map<String, String>, val rows: Map<String, List<Triple<String, String, String>>>)

object TemplateStore {
    private const val PREFS = "pdf_templates"; private const val KEY = "templates"
    fun list(c: Context, typeId: String) = all(c).filter { it.typeId == typeId }
    fun listAll(c: Context) = all(c)
    private fun all(c: Context): List<Template> { val out = mutableListOf<Template>()
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) runCatching { val o = arr.getJSONObject(i)
            val fields = mutableMapOf<String, String>()
            o.getJSONObject("fields").keys().forEach { k -> fields[k] = o.getJSONObject("fields").optString(k) }
            val rows = mutableMapOf<String, List<Triple<String, String, String>>>()
            val ro = o.getJSONObject("rows"); ro.keys().forEach { key ->
                val ra = ro.getJSONArray(key); val l = mutableListOf<Triple<String, String, String>>()
                for (j in 0 until ra.length()) { val r = ra.getJSONArray(j)
                    l.add(Triple(r.optString(0), r.optString(1), r.optString(2))) }
                rows[key] = l }
            out.add(Template(o.getString("id"), o.getString("name"), o.getString("type"), fields, rows)) }
        return out.sortedBy { it.name.lowercase() } }
    fun save(c: Context, name: String, typeId: String, fields: Map<String, String>, rows: Map<String, List<Triple<String, String, String>>>) {
        val all = all(c).toMutableList(); all.removeAll { it.name.equals(name, true) && it.typeId == typeId }
        all.add(Template(UUID.randomUUID().toString(), name, typeId, fields, rows)); persist(c, all) }
    fun delete(c: Context, id: String) = persist(c, all(c).filterNot { it.id == id })
    fun rename(c: Context, id: String, newName: String): Boolean {
        val name = newName.trim(); if (name.isEmpty()) return false
        val all = all(c).toMutableList(); val idx = all.indexOfFirst { it.id == id }; if (idx < 0) return false
        val t = all[idx]; var final = name
        if (all.any { it.id != id && it.typeId == t.typeId && it.name.equals(final, true) }) {
            var k = 2
            while (all.any { it.id != id && it.typeId == t.typeId && it.name.equals("$final ($k)", true) }) k++
            final = "$final ($k)" }
        all[idx] = t.copy(name = final); persist(c, all); return true }
    fun defaultId(c: Context, typeId: String) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("default_$typeId", null)
    fun setDefault(c: Context, typeId: String, id: String?) {
        val e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (id == null) e.remove("default_$typeId") else e.putString("default_$typeId", id); e.apply() }
    fun encodeForm(fields: Map<String, String>, rows: Map<String, List<Triple<String, String, String>>>): String {
        val o = JSONObject(); val f = JSONObject(); fields.forEach { (k, v) -> f.put(k, v) }
        val r = JSONObject(); rows.forEach { (key, list) -> val a = JSONArray()
            list.forEach { a.put(JSONArray().put(it.first).put(it.second).put(it.third)) }; r.put(key, a) }
        o.put("fields", f); o.put("rows", r); return o.toString() }
    fun decodeForm(json: String): Pair<Map<String, String>, Map<String, List<Triple<String, String, String>>>>? = runCatching {
        val o = JSONObject(json); val f = mutableMapOf<String, String>()
        val fo = o.getJSONObject("fields"); fo.keys().forEach { k -> f[k] = fo.optString(k) }
        val r = mutableMapOf<String, List<Triple<String, String, String>>>()
        val ro = o.getJSONObject("rows"); ro.keys().forEach { key ->
            val ra = ro.getJSONArray(key); val l = mutableListOf<Triple<String, String, String>>()
            for (j in 0 until ra.length()) { val a = ra.getJSONArray(j)
                l.add(Triple(a.optString(0), a.optString(1), a.optString(2))) }
            r[key] = l }
        f to r }.getOrNull()
    data class ImportResult(val imported: Int, val skipped: Int)
    fun exportJson(c: Context): String { val arr = JSONArray()
        all(c).forEach { t -> val fields = JSONObject(); t.fields.forEach { (k, v) -> fields.put(k, v) }
            val rows = JSONObject(); t.rows.forEach { (key, list) -> val ra = JSONArray()
                list.forEach { ra.put(JSONArray().put(it.first).put(it.second).put(it.third)) }; rows.put(key, ra) }
            arr.put(JSONObject().put("name", t.name).put("type", t.typeId).put("fields", fields).put("rows", rows)) }
        return JSONObject().put("app", "PDFMaker").put("version", 1).put("templates", arr).toString(2) }
    fun importJson(c: Context, json: String): ImportResult {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return ImportResult(0, 0)
        val arr = root.optJSONArray("templates") ?: return ImportResult(0, 0)
        val current = all(c).toMutableList(); var imported = 0; var skipped = 0
        for (i in 0 until arr.length()) runCatching { val o = arr.getJSONObject(i)
            val typeId = o.optString("type"); val name = o.optString("name").trim()
            if (typeId.isBlank() || name.isBlank() || PdfCatalog.byId[typeId] == null) { skipped++; return@runCatching }
            val fields = mutableMapOf<String, String>()
            o.optJSONObject("fields")?.let { fo -> fo.keys().forEach { k -> fields[k] = fo.optString(k) } }
            val rows = mutableMapOf<String, List<Triple<String, String, String>>>()
            o.optJSONObject("rows")?.let { ro -> ro.keys().forEach { key ->
                val ra = ro.getJSONArray(key); val l = mutableListOf<Triple<String, String, String>>()
                for (j in 0 until ra.length()) { val r = ra.getJSONArray(j)
                    l.add(Triple(r.optString(0), r.optString(1), r.optString(2))) }
                rows[key] = l } }
            var finalName = name
            if (current.any { it.typeId == typeId && it.name.equals(finalName, true) }) { var n = 2
                while (current.any { it.typeId == typeId && it.name.equals("$finalName ($n)", true) }) n++
                finalName = "$finalName ($n)" }
            current.add(Template(UUID.randomUUID().toString(), finalName, typeId, fields, rows)); imported++ }
        if (imported > 0) persist(c, current); return ImportResult(imported, skipped) }
    private fun persist(c: Context, templates: List<Template>) { val arr = JSONArray()
        templates.forEach { t -> val fields = JSONObject(); t.fields.forEach { (k, v) -> fields.put(k, v) }
            val rows = JSONObject(); t.rows.forEach { (key, list) -> val ra = JSONArray()
                list.forEach { ra.put(JSONArray().put(it.first).put(it.second).put(it.third)) }; rows.put(key, ra) }
            arr.put(JSONObject().put("id", t.id).put("name", t.name).put("type", t.typeId).put("fields", fields).put("rows", rows)) }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply() }
}
