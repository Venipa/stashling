package net.venipa.stashling.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.Base64

// File datastore/secrets.preferences_pb: excluded from backup and device transfer in data_extraction_rules.xml.
private val Context.secretsData by preferencesDataStore(name = "secrets")

/** Each value is the Keystore-sealed JSON of the account; an unreadable value (restored to a new device, reset key) loads as signed out. */
class SecretStoreImpl internal constructor(
    context: Context,
    private val box: SecretBox,
) : SecretStore {
    constructor(context: Context) : this(context, SecretBox())

    private val data = context.applicationContext.secretsData

    // shortcut: the first read blocks its caller (one small file + one Keystore decrypt) so the gate never flashes onboarding
    // on a cold start; move to an async splash if it shows up in startup traces.
    private val initial: Preferences by lazy { runBlocking { data.data.first() } }
    private val stashFlow by lazy { MutableStateFlow(initial[STASH]?.let { unseal(it, StashAccount.serializer()) }) }
    private val whisparrFlow by lazy { MutableStateFlow(initial[WHISPARR]?.let { unseal(it, WhisparrAccount.serializer()) }) }

    override val stash: StateFlow<StashAccount?> get() = stashFlow
    override val whisparr: StateFlow<WhisparrAccount?> get() = whisparrFlow

    override suspend fun setStash(account: StashAccount?) {
        write(STASH, account?.let { seal(it, StashAccount.serializer()) })
        stashFlow.value = account
    }

    override suspend fun setWhisparr(account: WhisparrAccount?) {
        write(WHISPARR, account?.let { seal(it, WhisparrAccount.serializer()) })
        whisparrFlow.value = account
    }

    private suspend fun write(key: Preferences.Key<String>, sealed: String?) {
        data.edit { if (sealed == null) it.remove(key) else it[key] = sealed }
    }

    private fun <T> seal(value: T, serializer: KSerializer<T>): String = Base64.getEncoder().encodeToString(box.seal(Json.encodeToString(serializer, value).encodeToByteArray()))

    private fun <T> unseal(sealed: String, serializer: KSerializer<T>): T? {
        val blob = runCatching { Base64.getDecoder().decode(sealed) }.getOrNull() ?: return null
        return box.open(blob)?.let { runCatching { Json.decodeFromString(serializer, it.decodeToString()) }.getOrNull() }
    }

    private companion object {
        val STASH = stringPreferencesKey("stash")
        val WHISPARR = stringPreferencesKey("whisparr")
    }
}
