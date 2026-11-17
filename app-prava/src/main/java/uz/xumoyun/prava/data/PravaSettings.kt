package uz.xumoyun.prava.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uz.xumoyun.prava.domain.Category
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PravaSettings @Inject constructor(private val store: DataStore<Preferences>) {

    val category: Flow<Category> = store.data.map { p ->
        p[CATEGORY]?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: Category.B
    }.distinctUntilChanged()

    val soundOn: Flow<Boolean> = store.data.map { it[SOUND] ?: true }.distinctUntilChanged()

    suspend fun setCategory(c: Category) = store.edit { it[CATEGORY] = c.name }
    suspend fun setSound(on: Boolean) = store.edit { it[SOUND] = on }
    suspend fun currentCategory(): Category = category.first()

    suspend fun contentVersion(): Int? = store.data.first()[CONTENT_VERSION]
    suspend fun setContentVersion(v: Int) = store.edit { it[CONTENT_VERSION] = v }

    private companion object {
        val CATEGORY = stringPreferencesKey("category")
        val SOUND = booleanPreferencesKey("sound")
        val CONTENT_VERSION = intPreferencesKey("content_version")
    }
}
