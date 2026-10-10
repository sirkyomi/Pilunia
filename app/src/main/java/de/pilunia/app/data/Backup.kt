package de.pilunia.app.data

import android.content.Context
import android.net.Uri
import de.pilunia.app.PiluniaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Inhalt einer Sicherungsdatei (JSON). */
@Serializable
data class BackupFile(
    val format: Int = 1,
    val createdAt: Long,
    val medications: List<Medication>,
    val doses: List<Dose>,
    val intakes: List<Intake>,
    val settings: AppSettings,
)

/** „Backup & Wiederherstellen“: Sicherung als JSON-Datei über das Android-Dateisystem. */
object Backup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    /** Vorschlag für den Dateinamen, z. B. „pilunia-backup-2026-10-10.json“. */
    fun suggestedName(): String = "pilunia-backup-${java.time.LocalDate.now()}.json"

    /** Schreibt die Sicherung in [uri] (aus ACTION_CREATE_DOCUMENT). */
    suspend fun write(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val c = PiluniaApp.container(context)
            val meds = c.repository.meds()
            val file = BackupFile(
                createdAt = System.currentTimeMillis(),
                medications = meds.map { it.med },
                doses = meds.flatMap { it.doses },
                intakes = c.repository.intakes(),
                settings = c.settings.current(),
            )
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.encodeToString(file).toByteArray()) }
            c.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        }
    }

    /**
     * Stellt eine Sicherung aus [uri] (aus ACTION_OPEN_DOCUMENT) wieder her. Alle vorhandenen Daten
     * werden ersetzt. Das Onboarding gilt danach als erledigt.
     * @return Anzahl der wiederhergestellten Medikamente
     */
    suspend fun restore(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val file = json.decodeFromString<BackupFile>(text)
            val c = PiluniaApp.container(context)
            val byMed = file.doses.groupBy { it.medId }
            c.repository.replaceAll(file.medications.map { MedWithDoses(it, byMed[it.id].orEmpty()) }, file.intakes)
            c.settings.update { file.settings.copy(onboardingDone = true, lastBackupAt = it.lastBackupAt) }
            c.reschedule()
            file.medications.size
        }
    }
}
