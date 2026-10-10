package de.pilunia.app.export

import android.content.Context
import android.net.Uri
import de.pilunia.app.PiluniaApp
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.toLocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * „Daten exportieren“: der gesamte Verlauf als CSV-Datei
 * (Semikolon-getrennt, UTF-8 mit BOM, damit Excel Umlaute richtig erkennt).
 */
object CsvExport {
    const val MIME = "text/csv"

    private val header = listOf("Datum", "Geplante Zeit", "Medikament", "Stärke", "Menge", "Status", "Genommen um")

    /** „pilunia-verlauf-2026-10-10.csv“ */
    fun suggestedName(): String = "pilunia-verlauf-${LocalDate.now()}.csv"

    /** Baut den CSV-Text. @return Text und Anzahl der Zeilen (ohne Kopfzeile). */
    suspend fun build(context: Context): Pair<String, Int> = withContext(Dispatchers.Default) {
        val repo = PiluniaApp.container(context).repository
        val entries = buildHistory(repo.meds(), repo.intakes())
        val sb = StringBuilder()
        sb.append('﻿')
        sb.append(header.joinToString(";") { esc(it) }).append("\r\n")
        for (e in entries) {
            val planned = if (e.status == EntryStatus.AS_NEEDED) "" else Fmt.time(e.minute)
            val takenAt = e.takenAt?.let { ms ->
                val t = ms.toLocalDateTime()
                if (t.toLocalDate() == e.day) Fmt.time(ms) else "${Fmt.numeric(t.toLocalDate())} ${Fmt.time(ms)}"
            }.orEmpty()
            val row = listOf(
                Fmt.numeric(e.day),
                planned,
                e.med.name,
                e.med.strengthText,
                e.med.form.amount(e.amount),
                e.status.label,
                if (e.status == EntryStatus.TAKEN || e.status == EntryStatus.AS_NEEDED) takenAt else "",
            )
            sb.append(row.joinToString(";") { esc(it) }).append("\r\n")
        }
        sb.toString() to entries.size
    }

    /** Schreibt die CSV-Datei in [uri] (aus CreateDocument). @return Anzahl der Zeilen. */
    suspend fun write(context: Context, uri: Uri): Result<Int> = runCatching {
        val (text, rows) = build(context)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        rows
    }

    /** Legt die CSV-Datei im Cache ab (zum Teilen). */
    suspend fun cacheFile(context: Context): Result<File> = runCatching {
        val (text, _) = build(context)
        withContext(Dispatchers.IO) {
            exportFile(context, suggestedName()).apply { writeBytes(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun esc(v: String): String =
        if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
