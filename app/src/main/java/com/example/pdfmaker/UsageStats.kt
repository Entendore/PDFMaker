package com.example.pdfmaker
import android.content.Context

object UsageStats {
    data class Entry(val timeMs: Long, val typeId: String)
    fun recordType(c: Context, typeId: String) { runCatching {
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val l = p.getString("type_log2","")?.split(',')?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        l.add(0, "${System.currentTimeMillis()}:$typeId"); while (l.size > 1000) l.removeAt(l.size-1)
        p.edit().putString("type_log2", l.joinToString(",")).apply() } }
    fun entries(c: Context): List<Entry> { val out = mutableListOf<Entry>(); runCatching {
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        p.getString("type_log2","")?.split(',')?.filter { it.isNotBlank() }?.forEach { s ->
            val i = s.indexOf(':'); if (i > 0) out.add(Entry(s.substring(0,i).toLongOrNull() ?: 0L, s.substring(i+1))) }
        p.getString("type_log","")?.split(',')?.filter { it.isNotBlank() }?.forEach { out.add(Entry(0L, it)) } }
        return out }
    fun topTypes(c: Context, n: Int = 3) = entries(c).groupingBy { it.typeId }.eachCount().entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
    fun topTypesIn(c: Context, fromMs: Long, toMs: Long, n: Int = 5) =
        entries(c).filter { it.timeMs in fromMs until toMs }.groupingBy { it.typeId }.eachCount().entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
}
