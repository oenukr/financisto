// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appstate

import androidx.appstate.datastore.PersistToDataStore
import androidx.appstate.statestore.StateStoreKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.transaction.model.TransactionDraft

@Serializable
data class SavedDraftEntry(
    val draftId: String,
    val transactionId: Long,
)

@Serializable
data class AiDraftSessionState(
    val pendingDraft: TransactionDraft? = null,
    val savedHistory: List<SavedDraftEntry> = emptyList(),
)

data object AiDraftSessionKey :
    StateStoreKey<AiDraftSessionState>(defaultValue = AiDraftSessionState()),
    PersistToDataStore<AiDraftSessionState> {
    override val name: String = "ai_draft_session"
    override val serializer: KSerializer<AiDraftSessionState> = AiDraftSessionState.serializer()
}

data object BackupFolderUriKey :
    StateStoreKey<String>(defaultValue = Export.DEFAULT_EXPORT_PATH.absolutePath),
    PersistToDataStore<String> {
    override val name: String = "backup_folder_uri"
    override val serializer: KSerializer<String> = String.serializer()
}
