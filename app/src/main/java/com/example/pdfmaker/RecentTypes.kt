package com.example.pdfmaker
import android.content.Context

object RecentTypes {
    fun record(c: Context, typeId: String) {
        val l = list(c).toMutableList(); l.remove(typeId); l.add(0, typeId)
        while (l.size > 6) l.removeAt(l.size-1)
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("recent_types", l.joinToString(",")).apply()
    }
    fun list(c: Context): List<String> =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("recent_types","")?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
}
