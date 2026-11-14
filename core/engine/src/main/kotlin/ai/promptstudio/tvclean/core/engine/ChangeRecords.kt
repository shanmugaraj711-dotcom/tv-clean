package ai.promptstudio.tvclean.core.engine

import ai.promptstudio.tvclean.core.model.ChangeRecord
import ai.promptstudio.tvclean.core.model.Op
import ai.promptstudio.tvclean.core.ops.UndoOrder

// ChangeRecord (R0: 1) is derived from journals: every write op this app completed, minus the
// ones a rollback or an undo put back. Undo and the restore files (W6, W8) start from this list.
object ChangeRecords {
    // The ops that change the TV. Reads, waits, the watchdog and process starts are not changes.
    fun isChange(op: Op): Boolean = when (op) {
        // RFC-TRIM-1: the trim writes to the TV (it deletes cached data), so it is a change.
        is Op.Disable, is Op.Enable, is Op.DefaultState, is Op.Install, is Op.InstallStream, is Op.Uninstall,
        is Op.SettingsPut, is Op.SetHome, is Op.PushAsset, is Op.RemoveFile, is Op.TrimCaches,
        is Op.Suspend, is Op.Unsuspend -> true
        else -> false
    }

    // Records in journal order. A Rollback record switches the reading to undo mode: a Done(i)
    // after it removes the change made by step i (i = -1 marks a rollback op that undoes no step).
    fun fromJournal(records: List<JournalRecord>): List<ChangeRecord> {
        val changes = linkedMapOf<Int, ChangeRecord>()
        val begun = mutableMapOf<Int, JournalRecord.Begin>()
        var undoing = false
        for (r in records) {
            when (r) {
                is JournalRecord.Begin -> begun[r.i] = r
                is JournalRecord.Done -> {
                    val b = begun.remove(r.i) ?: continue
                    if (undoing) changes.remove(r.i)
                    else if (isChange(b.op)) changes[r.i] = ChangeRecord(b.planId, r.i, b.op, b.prior, b.atMs)
                }
                is JournalRecord.Failed -> begun.remove(r.i)
                is JournalRecord.Rollback -> undoing = true
                else -> {}
            }
        }
        return changes.values.toList()
    }

    fun fromJournals(journals: List<List<JournalRecord>>): List<ChangeRecord> =
        journals.flatMap { fromJournal(it) }.sortedWith(UndoOrder.CHRONOLOGICAL)
}
