package de.pilunia.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.pilunia.app.ui.theme.Fonts
import de.pilunia.app.PiluniaApp
import de.pilunia.app.R
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.adherence
import de.pilunia.app.domain.forecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * „Für deinen Arzttermin“: Einnahmeplan, Vorrat, Einnahmetreue der letzten 30 Tage
 * und verpasste Einnahmen als A4-PDF in den App-Farben und -Schriften.
 */
object PdfReport {
    const val MIME = "application/pdf"

    fun fileName(): String = "pilunia-bericht-${LocalDate.now()}.pdf"

    /** Erstellt den Bericht in cacheDir/exports/ und gibt die Datei zurück. */
    suspend fun create(context: Context): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val c = PiluniaApp.container(context)
            val meds = c.repository.meds().sortedBy { it.med.name.lowercase() }
            val intakes = c.repository.intakes()
            val settings = c.settings.current()
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val from = today.minusDays(29)

            val w = Writer(context)
            w.header(settings.userName.trim(), from, today)

            // ---------- Einnahmeplan ----------
            w.section("Einnahmeplan")
            if (meds.isEmpty()) w.paragraph("Es sind noch keine Medikamente angelegt.")
            for (m in meds) w.medBlock(m, forecastText(m, intakes))

            // ---------- Einnahmetreue ----------
            val planned = meds.filter { it.med.scheduleType != ScheduleType.AS_NEEDED }
            val total = adherence(from, today, planned, intakes, now)
            w.section("Einnahmetreue · letzte 30 Tage")
            if (total.due == 0) {
                w.paragraph("Im Zeitraum waren keine Einnahmen fällig.")
            } else {
                w.adherenceSummary(total.percent, total.taken, total.due, total.missed)
                for (m in planned) {
                    val a = adherence(from, today, listOf(m), intakes, now)
                    if (a.due == 0) continue
                    w.adherenceBar(m.med.displayName, a.percent, a.missed)
                }
            }

            // ---------- Verpasste Einnahmen ----------
            val missed = buildHistory(meds, intakes, now, from, today)
                .filter { it.status == EntryStatus.MISSED || it.status == EntryStatus.SKIPPED }
            w.section("Verpasste und ausgelassene Einnahmen")
            if (missed.isEmpty()) {
                w.paragraph("In den letzten 30 Tagen wurde keine Einnahme verpasst.")
            } else {
                w.table(
                    listOf("Datum", "Geplant", "Medikament", "Menge", "Status"),
                    listOf(0.20f, 0.13f, 0.37f, 0.15f, 0.15f),
                    missed.map {
                        listOf(
                            Fmt.dayShortWithYear(it.day), Fmt.time(it.minute), it.med.displayName,
                            it.med.form.amount(it.amount), it.status.label,
                        )
                    },
                    highlight = { row -> row[4] == EntryStatus.MISSED.label },
                )
            }

            val file = exportFile(context, fileName())
            withContext(Dispatchers.IO) { w.finish(file) }
            file
        }
    }

    private fun forecastText(m: MedWithDoses, intakes: List<de.pilunia.app.data.Intake>): String {
        val med = m.med
        val stock = "Vorrat: ${med.form.amount(med.stock.coerceAtLeast(0))}"
        if (med.scheduleType == ScheduleType.AS_NEEDED) return stock
        val f = forecast(m, intakes)
        return when {
            f.firstEmptyDay == null -> stock
            f.daysLeft == 0 -> "$stock · reicht nicht mehr für heute"
            else -> "$stock · reicht bis ${Fmt.dayShortWithYear(f.lastCoveredDay!!)}"
        }
    }

    // =================================================================
    // Zeichnen
    // =================================================================

    private const val W = 595f
    private const val H = 842f
    private const val M = 40f
    private const val CW = W - 2 * M

    private const val PRIMAER = 0xFF7A5CC7.toInt()
    private const val VERLAUF_ENDE = 0xFF6A4FB5.toInt()
    private const val TINTE = 0xFF2E2440.toInt()
    private const val GEDAEMPFT = 0xFF5F5676.toInt()
    private const val LINIE = 0xFFF0EAF6.toInt()
    private const val TOENUNG = 0xFFF3EEFE.toInt()
    private const val TOENUNG_KRAEFTIG = 0xFFEDE5FE.toInt()
    private const val WARNUNG = 0xFFB4234A.toInt()
    private const val WARNUNG_FLAECHE = 0xFFFDE6EC.toInt()
    private const val ROSE_BALKEN = 0xFFF48FAA.toInt()
    private const val WEISS = 0xFFFFFFFF.toInt()

    private fun swatch(c: MedColor): Int = when (c) {
        MedColor.ROSE -> 0xFFF48FAA.toInt()
        MedColor.GELB -> 0xFFFFC94D.toInt()
        MedColor.MINT -> 0xFF7FD3A6.toInt()
        MedColor.LAVENDEL -> 0xFFA78BFA.toInt()
        MedColor.BLAU -> 0xFF8EB8F0.toInt()
        MedColor.GRAU -> 0xFF94A3B8.toInt()
    }

    private class Writer(private val context: Context) {
        private val doc = PdfDocument()
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f

        fun paint(outfitFont: Boolean, size: Float, weight: Int, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Fonts.typeface(if (outfitFont) Fonts.OUTFIT else Fonts.ATKINSON, weight)
            textSize = size
            this.color = color
        }

        private val footerPaint = paint(false, 8.5f, 400, GEDAEMPFT)

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            page = p
            canvas = p.canvas
            y = M
            canvas.drawText("Pilunia ersetzt keine ärztliche Beratung.", M, H - 22f, footerPaint)
            val s = "Seite $pageNo"
            canvas.drawText(s, W - M - footerPaint.measureText(s), H - 22f, footerPaint)
        }

        private fun ensure(h: Float) {
            if (page == null || y + h > H - M - 16f) newPage()
        }

        fun wrap(text: String, p: Paint, width: Float): List<String> {
            val out = mutableListOf<String>()
            for (para in text.split('\n')) {
                var line = ""
                for (word in para.split(' ')) {
                    val cand = if (line.isEmpty()) word else "$line $word"
                    if (p.measureText(cand) <= width) line = cand
                    else {
                        if (line.isNotEmpty()) out += line
                        // Sehr lange Wörter hart umbrechen.
                        var rest = word
                        while (p.measureText(rest) > width && rest.length > 1) {
                            var n = rest.length
                            while (n > 1 && p.measureText(rest, 0, n) > width) n--
                            out += rest.substring(0, n)
                            rest = rest.substring(n)
                        }
                        line = rest
                    }
                }
                out += line
            }
            return out
        }

        private fun ellipsize(text: String, p: Paint, width: Float): String {
            if (p.measureText(text) <= width) return text
            var n = text.length
            while (n > 0 && p.measureText(text.substring(0, n) + "…") > width) n--
            return text.substring(0, n) + "…"
        }

        fun header(name: String, from: LocalDate, to: LocalDate) {
            newPage()
            val h = 112f
            val r = RectF(M, y, W - M, y + h)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(r.left, r.top, r.right, r.bottom, PRIMAER, VERLAUF_ENDE, Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(r, 22f, 22f, bg)
            // Pilunia-Figur rechts im hellen Kreis.
            val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WEISS; alpha = 31 }
            canvas.drawCircle(r.right - 62f, r.top + h / 2 + 2f, 46f, circle)
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp: Bitmap? = BitmapFactory.decodeResource(context.resources, R.drawable.pilunia_herz, opts)
                if (bmp != null) {
                    val dst = RectF(r.right - 110f, r.top + 8f, r.right - 14f, r.top + 104f)
                    canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                }
            }
            val title = paint(true, 22f, 700, WEISS)
            val sub = paint(false, 11.5f, 400, WEISS)
            val subBold = paint(false, 11.5f, 700, WEISS)
            canvas.drawText("Einnahmebericht", r.left + 22f, r.top + 38f, title)
            var ly = r.top + 62f
            if (name.isNotEmpty()) {
                canvas.drawText("Für $name", r.left + 22f, ly, subBold)
                ly += 17f
            }
            canvas.drawText("Zeitraum: ${Fmt.numeric(from)} – ${Fmt.numeric(to)} (letzte 30 Tage)", r.left + 22f, ly, sub)
            ly += 17f
            canvas.drawText("Erstellt am ${Fmt.numeric(LocalDate.now())} mit Pilunia", r.left + 22f, ly, sub)
            y += h + 8f
        }

        fun section(title: String) {
            // Überschrift nie allein am Seitenende.
            ensure(110f)
            y += 18f
            val p = paint(true, 16f, 600, TINTE)
            canvas.drawText(title, M, y + 14f, p)
            y += 26f
        }

        fun paragraph(text: String) {
            val p = paint(false, 11f, 400, GEDAEMPFT)
            for (line in wrap(text, p, CW)) {
                ensure(16f)
                canvas.drawText(line, M, y + 11f, p)
                y += 16f
            }
            y += 4f
        }

        fun medBlock(m: MedWithDoses, stockText: String) {
            val med = m.med
            val name = paint(false, 12.5f, 700, TINTE)
            val schedule = paint(false, 10.5f, 700, PRIMAER)
            val body = paint(false, 10.5f, 400, TINTE)
            val muted = paint(false, 10.5f, 400, GEDAEMPFT)
            val time = paint(true, 11f, 600, TINTE)
            val pad = 14f
            val inner = CW - 2 * pad - 6f

            val info = listOf(med.purpose, med.form.label).filter { it.isNotBlank() }.joinToString(" · ")
            val doseLines = m.sortedDoses.map { d ->
                Triple(Fmt.time(d.minuteOfDay), med.form.amount(d.amount), d.label)
            }
            val hintLines = if (med.hint.isNotBlank()) wrap("Hinweis: ${med.hint}", muted, inner) else emptyList()
            val noteLines = if (med.prescriptionNote.isNotBlank()) wrap("Rezept: ${med.prescriptionNote}", muted, inner) else emptyList()
            val stockLines = wrap(stockText, muted, inner)

            val lh = 15f
            var h = pad + 16f + (if (info.isNotEmpty()) lh else 0f) + 4f
            h += doseLines.size * lh + (if (doseLines.isEmpty() && med.scheduleType != ScheduleType.AS_NEEDED) lh else 0f)
            h += (hintLines.size + noteLines.size + stockLines.size) * lh + pad
            ensure(h + 8f)

            val r = RectF(M, y, W - M, y + h)
            canvas.drawRoundRect(r, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TOENUNG })
            canvas.drawRoundRect(RectF(r.left, r.top, r.left + 6f, r.bottom), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = swatch(med.color) })
            val x = r.left + pad + 6f
            var ty = r.top + pad + 12f
            val sched = med.scheduleText()
            val schedW = schedule.measureText(sched).coerceAtMost(inner * 0.5f)
            canvas.drawText(ellipsize(med.displayName, name, inner - schedW - 12f), x, ty, name)
            canvas.drawText(ellipsize(sched, schedule, schedW + 1f), r.right - pad - schedW, ty, schedule)
            ty += 4f
            if (info.isNotEmpty()) { ty += lh; canvas.drawText(ellipsize(info, muted, inner), x, ty, muted) }
            ty += 4f
            for ((t, amount, label) in doseLines) {
                ty += lh
                canvas.drawText(t, x, ty, time)
                canvas.drawText(amount, x + 48f, ty, body)
                if (label.isNotBlank()) canvas.drawText(ellipsize(label, muted, inner - 160f), x + 160f, ty, muted)
            }
            if (doseLines.isEmpty() && med.scheduleType != ScheduleType.AS_NEEDED) {
                ty += lh; canvas.drawText("Keine Uhrzeiten festgelegt", x, ty, muted)
            }
            for (l in hintLines + noteLines + stockLines) { ty += lh; canvas.drawText(l, x, ty, muted) }
            y += h + 8f
        }

        fun adherenceSummary(percent: Int, taken: Int, due: Int, missed: Int) {
            ensure(56f)
            val big = paint(true, 30f, 800, PRIMAER)
            val label = paint(false, 11f, 700, TINTE)
            val muted = paint(false, 11f, 400, GEDAEMPFT)
            val pct = "$percent %"
            canvas.drawText(pct, M, y + 30f, big)
            val x = M + big.measureText(pct) + 16f
            canvas.drawText("Einnahmetreue gesamt", x, y + 14f, label)
            canvas.drawText("$taken von $due Einnahmen genommen · $missed verpasst oder ausgelassen", x, y + 30f, muted)
            y += 46f
        }

        fun adherenceBar(name: String, percent: Int, missed: Int) {
            ensure(34f)
            val n = paint(false, 11f, 700, TINTE)
            val v = paint(false, 10.5f, 700, if (missed > 0) WARNUNG else TINTE)
            val value = if (missed > 0) "$percent % · $missed verpasst" else "$percent %"
            val vw = v.measureText(value)
            canvas.drawText(ellipsize(name, n, CW - vw - 12f), M, y + 11f, n)
            canvas.drawText(value, W - M - vw, y + 11f, v)
            val top = y + 17f
            val track = RectF(M, top, W - M, top + 6f)
            canvas.drawRoundRect(track, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (missed > 0) WARNUNG_FLAECHE else TOENUNG_KRAEFTIG })
            val fill = RectF(M, top, M + CW * percent / 100f, top + 6f)
            if (percent > 0) canvas.drawRoundRect(fill, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (missed > 0) ROSE_BALKEN else PRIMAER })
            y += 32f
        }

        fun table(head: List<String>, widths: List<Float>, rows: List<List<String>>, highlight: (List<String>) -> Boolean) {
            val hp = paint(false, 10f, 700, TINTE)
            val bp = paint(false, 10f, 400, TINTE)
            val warn = paint(false, 10f, 700, WARNUNG)
            val muted = paint(false, 10f, 400, GEDAEMPFT)
            val rowH = 20f
            fun drawHead() {
                ensure(rowH * 2)
                canvas.drawRoundRect(RectF(M, y, W - M, y + rowH), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TOENUNG })
                var x = M + 8f
                head.forEachIndexed { i, t -> canvas.drawText(t, x, y + 13.5f, hp); x += CW * widths[i] }
                y += rowH
            }
            drawHead()
            val line = Paint().apply { color = LINIE; strokeWidth = 0.8f }
            for (r in rows) {
                if (y + rowH > H - M - 16f) { newPage(); drawHead() }
                var x = M + 8f
                r.forEachIndexed { i, t ->
                    val p = when {
                        i == r.lastIndex -> if (highlight(r)) warn else muted
                        else -> bp
                    }
                    canvas.drawText(ellipsize(t, p, CW * widths[i] - 10f), x, y + 13.5f, p)
                    x += CW * widths[i]
                }
                y += rowH
                canvas.drawLine(M, y, W - M, y, line)
            }
            y += 6f
        }

        fun finish(file: File) {
            page?.let { doc.finishPage(it) }
            page = null
            file.outputStream().use { doc.writeTo(it) }
            doc.close()
        }
    }
}
