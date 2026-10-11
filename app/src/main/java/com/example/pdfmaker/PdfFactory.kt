package com.example.pdfmaker

import android.content.ContentResolver
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InvoiceItem(val name: String, val qty: Double, val price: Double)
data class InvoiceData(val number: String, val currency: String, val business: String,
    val businessDetails: String, val client: String, val clientDetails: String,
    val items: List<InvoiceItem>, val taxRate: Double, val notes: String) {
    val subtotal get() = items.sumOf { it.qty * it.price }
    val tax get() = subtotal * taxRate / 100.0
    val total get() = subtotal + tax }
data class ResumeData(val name: String, val headline: String, val email: String, val phone: String,
    val location: String, val summary: String, val experience: String, val education: String,
    val skills: List<String>)
data class CertificateData(val title: String, val recipient: String, val achievement: String,
    val presenter: String, val date: String)
data class PackingItem(val name: String, val qty: Double, val weight: Double)
data class PackingData(val reference: String, val sender: String, val senderDetails: String,
    val recipient: String, val recipientDetails: String, val carrier: String, val cartons: String,
    val items: List<PackingItem>, val notes: String)
data class ActionItem(val task: String, val owner: String, val due: String)
data class MinutesData(val title: String, val dateTime: String, val location: String,
    val attendees: String, val agenda: String, val notes: String, val decisions: String,
    val actions: List<ActionItem>)
data class FlyerData(val title: String, val tagline: String, val date: String, val time: String,
    val venue: String, val description: String, val contact: String)

object PdfFactory {
    fun today(): String = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())
    private fun money(symbol: String, v: Double) = symbol + String.format(Locale.US, "%.2f", v)
    private fun qty(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v)

    fun textDoc(w: PdfWriter, title: String, content: String, images: List<Uri>, resolver: ContentResolver) {
        w.newPage(title.ifBlank { "Document" }, today())
        if (content.isNotBlank()) w.text(content)
        images.forEach { w.image(it, resolver) }
    }

    fun invoice(w: PdfWriter, d: InvoiceData) {
        w.newPage("Invoice ${d.number}", today())
        w.twoColumns("From", listOf(d.business) + d.businessDetails.lines().filter { it.isNotBlank() },
            "Bill To", listOf(d.client) + d.clientDetails.lines().filter { it.isNotBlank() })
        w.spacer(8f)
        w.table(listOf("Item","Qty","Unit Price","Amount"),
            d.items.map { listOf(it.name, qty(it.qty), money(d.currency, it.price), money(d.currency, it.qty * it.price)) },
            floatArrayOf(2.4f,0.8f,1.2f,1.2f), rightCols = setOf(1,2,3))
        w.spacer(12f)
        w.keyValue("Subtotal", money(d.currency, d.subtotal))
        w.keyValue("Tax (${qty(d.taxRate)}%)", money(d.currency, d.tax))
        w.totalRow("TOTAL", money(d.currency, d.total))
        if (d.notes.isNotBlank()) { w.spacer(10f); w.text("Notes: ${d.notes}", w.small) }
    }

    fun resume(w: PdfWriter, d: ResumeData) {
        w.newPage()
        w.text(d.name.ifBlank { "Your Name" }, w.h1, spacing = 2f)
        if (d.headline.isNotBlank()) w.text(d.headline, w.h2, spacing = 4f)
        val contact = listOf(d.email, d.phone, d.location).filter { it.isNotBlank() }.joinToString("   •   ")
        if (contact.isNotEmpty()) w.text(contact, w.small, spacing = 6f)
        w.rule()
        if (d.summary.isNotBlank()) { w.heading("Summary"); w.text(d.summary) }
        if (d.experience.isNotBlank()) { w.heading("Experience"); entries(d.experience).forEach { entryBlock(w, it) } }
        if (d.skills.isNotEmpty()) { w.heading("Skills"); d.skills.forEach { w.bullet(it) } }
        if (d.education.isNotBlank()) { w.heading("Education"); entries(d.education).forEach { entryBlock(w, it) } }
    }

    private fun entryBlock(w: PdfWriter, e: List<String>) {
        w.text(e.first(), w.bodyBold, spacing = 2f)
        e.drop(1).forEach { w.bullet(it.trimStart('-', '•', ' ')) }
        w.spacer(8f)
    }

    private fun entries(block: String) = block.split(Regex("\n\\s*\n"))
        .map { b -> b.lines().map(String::trim).filter(String::isNotEmpty) }.filter { it.isNotEmpty() }

    fun certificate(w: PdfWriter, d: CertificateData) {
        w.newPage()
        val c = w.c; val W = w.pageW; val H = w.pageH
        c.drawRect(RectF(26f, 26f, W - 26f, H - 26f),
            Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = w.accentColor })
        c.drawRect(RectF(40f, 40f, W - 40f, H - 40f),
            Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = PdfWriter.LIGHT })
        fun serif(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfWriter.DARK; textSize = size; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val italic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfWriter.GRAY; textSize = 14f; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
        w.spacer(80f)
        w.drawCentered(d.title.ifBlank { "Certificate of Achievement" }, serif(30f), gap = 20f)
        w.drawCentered("This certificate is proudly presented to", italic, gap = 24f)
        w.drawCenteredWrapped(d.recipient.ifBlank { "Recipient Name" },
            serif(38f).apply { color = w.accentColor }, gap = 18f)
        w.drawCenteredWrapped(d.achievement.ifBlank { "for outstanding achievement" }, w.body)
        val lineY = H - 120f
        val pen = Paint().apply { color = PdfWriter.DARK; strokeWidth = 1f }
        val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.GRAY; textSize = 11f }
        c.drawLine(W / 2f - 200f, lineY, W / 2f - 30f, lineY, pen)
        c.drawLine(W / 2f + 30f, lineY, W / 2f + 200f, lineY, pen)
        c.drawText(d.date.ifBlank { today() }, W / 2f - 200f, lineY + 16f, lbl)
        c.drawText(d.presenter.ifBlank { "Presenter" }, W / 2f + 30f, lineY + 16f, lbl)
    }

    fun packingList(w: PdfWriter, d: PackingData) {
        w.newPage("Packing List" + if (d.reference.isNotBlank()) " ${d.reference}" else "", today())
        w.twoColumns("Ship From", listOf(d.sender.ifBlank { "Sender" }) + d.senderDetails.lines().filter { it.isNotBlank() },
            "Ship To", listOf(d.recipient.ifBlank { "Recipient" }) + d.recipientDetails.lines().filter { it.isNotBlank() })
        w.spacer(8f)
        w.table(listOf("#","Description","Qty","Weight"),
            d.items.mapIndexed { i, it -> listOf("${i+1}", it.name, qty(it.qty), "${qty(it.weight)} kg") },
            floatArrayOf(0.5f,2.8f,0.7f,1.0f), rightCols = setOf(2,3))
        w.spacer(12f)
        if (d.carrier.isNotBlank()) w.keyValue("Carrier / Method", d.carrier)
        if (d.cartons.isNotBlank()) w.keyValue("Total Cartons", d.cartons)
        w.keyValue("Total Quantity", qty(d.items.sumOf { it.qty }))
        w.totalRow("TOTAL WEIGHT", "${qty(d.items.sumOf { it.weight })} kg")
        if (d.notes.isNotBlank()) { w.spacer(10f); w.text("Notes: ${d.notes}", w.small) }
        w.signatureLine("Received By — Name / Signature / Date")
    }

    fun minutes(w: PdfWriter, d: MinutesData) {
        w.newPage(d.title.ifBlank { "Meeting Minutes" }, "Meeting Minutes • ${today()}")
        w.keyValue("Date & Time", d.dateTime)
        w.keyValue("Location", d.location)
        if (d.attendees.isNotBlank()) { w.spacer(4f); w.text("Attendees", w.bodyBold, spacing = 2f); w.text(d.attendees, spacing = 8f) }
        if (d.agenda.isNotBlank()) { w.heading("Agenda")
            d.agenda.lines().map(String::trim).filter(String::isNotEmpty)
                .forEachIndexed { i, item -> w.numbered(i + 1, item) } }
        if (d.notes.isNotBlank()) { w.heading("Discussion & Notes"); w.text(d.notes) }
        if (d.decisions.isNotBlank()) { w.heading("Decisions")
            d.decisions.lines().map(String::trim).filter(String::isNotEmpty)
                .forEach { w.bullet(it.trimStart('-', '•', ' ')) } }
        if (d.actions.isNotEmpty()) { w.heading("Action Items")
            w.table(listOf("Task","Owner","Due"),
                d.actions.map { listOf(it.task, it.owner.ifBlank { "—" }, it.due.ifBlank { "—" }) },
                floatArrayOf(2.6f,1.1f,0.9f)) }
    }

    fun flyer(w: PdfWriter, d: FlyerData) {
        w.newPage()
        val c = w.c; val W = w.pageW; val H = w.pageH
        val band = Paint().apply { color = w.accentColor }
        val rule = Paint().apply { color = PdfWriter.LIGHT; strokeWidth = 2f }
        fun ctext(s: String, p: Paint, yy: Float, gap: Float = 8f): Float {
            if (s.isBlank()) return yy
            c.drawText(s, W / 2f - p.measureText(s) / 2f, yy, p); return yy + p.textSize + gap }
        val bandH = 190f
        c.drawRect(RectF(0f, 0f, W, bandH), band)
        c.drawLine(0f, bandH, W, bandH, rule)
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 30f; isFakeBoldText = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val tagP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 14f; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
        var by = 64f
        for (l in w.wrap(d.title.ifBlank { "EVENT TITLE" }, titleP, W - 120f)) by = ctext(l, titleP, by, 4f)
        ctext(d.tagline, tagP, by + 2f)
        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.GRAY; textSize = 11f }
        val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.DARK; textSize = 22f; isFakeBoldText = true }
        val grayP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.GRAY; textSize = 14f }
        var y2 = bandH + 58f
        y2 = ctext("WHEN", labelP, y2, 12f)
        y2 = ctext(d.date, bigP, y2, 5f)
        y2 = ctext(d.time, grayP, y2, 5f)
        y2 += 14f
        y2 = ctext("WHERE", labelP, y2, 12f)
        for (l in w.wrap(d.venue, bigP, W - 160f)) y2 = ctext(l, bigP, y2, 5f)
        y2 += 18f
        c.save(); c.rotate(45f, W / 2f, y2)
        c.drawRect(RectF(W / 2f - 5f, y2 - 5f, W / 2f + 5f, y2 + 5f), band)
        c.restore(); y2 += 34f
        for (l in w.wrap(d.description, w.body, W - 140f)) {
            if (y2 > H - 120f) break
            y2 = ctext(l, w.body, y2, 6f) }
        c.drawRect(RectF(0f, H - 70f, W, H), band)
        val contactP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14f; isFakeBoldText = true }
        ctext(d.contact, contactP, H - 36f)
    }

    fun specDoc(w: PdfWriter, s: DocSpec, vals: Map<String, String>, rowsIn: List<Triple<String, String, String>>) {
        val v = { k: String -> vals[k]?.trim() ?: "" }
        val cur = v("currency").ifBlank { "$" }
        val title = v("docTitle").ifBlank { s.label }
        fun money(x: Double) = cur + String.format(Locale.US, "%.2f", x)
        fun plain(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else String.format(Locale.US, "%.2f", x)
        fun tableRows(): List<List<String>> {
            val t = s.table ?: return emptyList()
            return rowsIn.map { listOf(it.first, it.second, it.third) } + List(t.blankRows) { listOf("", "", "") } }
        fun drawTotals() {
            if (!s.totals || rowsIn.isEmpty() || s.table == null) return
            val sum = rowsIn.sumOf { it.toList().getOrNull(s.totalCol - 1)?.toDoubleOrNull() ?: 0.0 }
            val fmt = { x: Double -> if (s.totalMoney) money(x) else plain(x) }
            val taxRate = v("tax").toDoubleOrNull()
            if (taxRate != null) {
                val tax = sum * taxRate / 100.0
                w.keyValue("Subtotal", fmt(sum)); w.keyValue("Tax (${qty(taxRate)}%)", fmt(tax))
                w.totalRow("TOTAL", fmt(sum + tax))
            } else w.totalRow(if (s.totalMoney) "TOTAL" else "TOTAL ${s.table.c3}".uppercase(), fmt(sum)) }
        when (s.layout) {
            DocLayout.LETTER -> {
                w.newPage(title, today())
                s.fields.filter { !it.multi }.forEach { fd -> if (v(fd.key).isNotBlank()) w.keyValue(fd.label, v(fd.key)) }
                w.spacer(8f)
                s.fields.filter { it.multi }.forEach { fd ->
                    if (v(fd.key).isNotBlank()) { w.text(v(fd.key), spacing = 8f); w.spacer(10f) } }
                if (s.signature) w.signatureLine(v("signer").ifBlank { "Signature" }) }
            DocLayout.SHEET -> {
                w.newPage(title, today())
                s.fields.filter { !it.multi }.forEach { fd -> if (v(fd.key).isNotBlank()) w.keyValue(fd.label, v(fd.key)) }
                s.fields.filter { it.multi }.forEach { fd ->
                    if (v(fd.key).isBlank()) return@forEach
                    if (!fd.label.equals("Body", true)) w.heading(fd.label)
                    w.text(v(fd.key)); w.spacer(4f) }
                s.table?.let { t ->
                    w.spacer(4f)
                    w.table(listOf(t.c1, t.c2, t.c3), tableRows(), floatArrayOf(1.4f, 1f, 1f),
                        rightCols = buildSet { if (t.num2) add(1); if (t.num3) add(2) }) }
                drawTotals()
                if (s.signature) w.signatureLine(v("signer").ifBlank { "Signature" }) }
            DocLayout.CHECKBOX -> {
                w.newPage(title, today())
                s.fields.filter { !it.multi }.forEach { fd -> if (v(fd.key).isNotBlank()) w.keyValue(fd.label, v(fd.key)) }
                s.table?.let { t ->
                    w.spacer(4f)
                    val all = rowsIn.filter { it.first.isNotBlank() } + List(t.blankRows) { Triple("", "", "") }
                    val boxP = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = PdfWriter.DARK }
                    for (r in all) {
                        w.ensureSpace(26f); val y0 = w.y
                        w.c.drawRect(RectF(PdfWriter.MARGIN, y0 + 5f, PdfWriter.MARGIN + 12f, y0 + 17f), boxP)
                        if (r.first.isNotBlank()) w.c.drawText(r.first, PdfWriter.MARGIN + 20f, y0 + 15.5f, w.bodyBold)
                        if (r.second.isNotBlank()) w.c.drawText(r.second, PdfWriter.MARGIN + w.contentW * 0.55f, y0 + 15.5f, w.body)
                        if (r.third.isNotBlank()) w.c.drawText(r.third, w.pageW - PdfWriter.MARGIN - w.small.measureText(r.third), y0 + 15.5f, w.small)
                        w.c.drawLine(PdfWriter.MARGIN, y0 + 26f, w.pageW - PdfWriter.MARGIN, y0 + 26f, w.thin)
                        w.spacer(26f) } }
                s.fields.filter { it.multi }.forEach { fd ->
                    if (v(fd.key).isNotBlank()) { w.heading(fd.label); w.text(v(fd.key)) } } }
            DocLayout.CARD -> {
                w.newPage()
                val c = w.c; val W = w.pageW; val H = w.pageH
                c.drawRect(RectF(26f, 26f, W - 26f, H - 26f),
                    Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = w.accentColor })
                c.drawRect(RectF(38f, 38f, W - 38f, H - 38f),
                    Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = PdfWriter.LIGHT })
                val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = w.accentColor; textSize = 32f; isFakeBoldText = true
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
                val mid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.DARK; textSize = 20f }
                val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.GRAY; textSize = 11f }
                val ital = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = PdfWriter.GRAY; textSize = 14f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
                w.y = H * 0.20f
                for (l in w.wrap(v("occasion").ifBlank { s.label }, big, W - 120f)) w.drawCentered(l, big, 6f)
                if (v("host").isNotBlank()) w.drawCentered("hosted by ${v("host")}", ital, 26f)
                listOf("date","time","venue","rsvp").forEach { key ->
                    val fd = s.fields.firstOrNull { it.key == key } ?: return@forEach
                    if (v(key).isBlank()) return@forEach
                    w.drawCentered(fd.label.uppercase(), lab, 4f)
                    for (l in w.wrap(v(key), mid, W - 140f)) w.drawCentered(l, mid, 3f)
                    w.spacer(10f) } }
            DocLayout.LABEL -> {
                w.newPage()
                val c = w.c; val W = w.pageW; val H = w.pageH
                c.drawRect(RectF(20f, 20f, W - 20f, H - 20f),
                    Paint().apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = w.accentColor })
                val nameP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.DARK; textSize = 19f; isFakeBoldText = true }
                val toP = Paint(nameP).apply { color = w.accentColor; textSize = 23f }
                val addrP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.DARK; textSize = 13f }
                val labP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfWriter.GRAY; textSize = 10f }
                fun block(label: String, name: String, details: String, namePaint: Paint) {
                    w.ensureSpace(60f)
                    c.drawText(label, PdfWriter.MARGIN, w.y + labP.textSize, labP)
                    w.spacer(labP.textSize + 4f)
                    c.drawText(name.ifBlank { "—" }, PdfWriter.MARGIN, w.y + namePaint.textSize, namePaint)
                    w.spacer(namePaint.textSize + 4f)
                    for (l in w.wrap(details, addrP, w.contentW - 20f)) {
                        c.drawText(l, PdfWriter.MARGIN, w.y + addrP.textSize, addrP)
                        w.spacer(addrP.textSize + 3f) } }
                w.y = 64f
                block("FROM", v("fromName"), v("fromDetails"), nameP)
                w.spacer(34f)
                block("TO", v("toName"), v("toDetails"), toP)
                w.spacer(30f)
                val meta = listOf(v("weight"), v("ref")).filter { it.isNotBlank() }.joinToString("   •   ")
                if (meta.isNotBlank()) c.drawText(meta, PdfWriter.MARGIN, w.y + w.small.textSize, w.small) }
            DocLayout.CUSTOM -> Unit
        }
    }
}
