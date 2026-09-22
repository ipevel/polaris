// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.slte.app.utils.AppLog
import java.security.KeyStore

@Suppress("DEPRECATION")
internal object SecurePreferences {

    /** 任意加密存储实例降级为内存存储时置 true，UI 层可据此显示警告 */
    var isDegraded: Boolean = false
        private set

    fun create(
        context: Context,
        fileName: String,
        keyAlias: String,
    ): SharedPreferences {
        createEncrypted(context, fileName, keyAlias)?.let { return it }

        migrateLegacy(context, fileName, keyAlias)?.let { return it }

        AppLog.w(TAG, "加密存储创建失败，尝试自愈（删除损坏的偏好文件与 Keystore 密钥）: $fileName")
        runCatching { context.deleteSharedPreferences(fileName) }
        runCatching { deleteKeyStoreEntry(keyAlias) }
        createEncrypted(context, fileName, keyAlias)?.let { return it }

        AppLog.e(
            TAG,
            "加密存储不可用，已降级为内存存储（本次运行的数据不会持久化）: $fileName",
        )
        isDegraded = true
        return InMemoryPreferences()
    }

    private fun migrateLegacy(
        context: Context,
        fileName: String,
        keyAlias: String,
    ): SharedPreferences? {
        val legacy = createEncrypted(context, fileName, LEGACY_KEY_ALIAS) ?: return null
        AppLog.w(TAG, "检测到旧版本加密数据，迁移到新别名: $fileName")
        val snapshot = legacy.all
        runCatching { context.deleteSharedPreferences(fileName) }
        val fresh = createEncrypted(context, fileName, keyAlias) ?: return null
        if (snapshot.isNotEmpty()) {
            val editor = fresh.edit()
            snapshot.forEach { (k, v) ->
                when (v) {
                    is String -> editor.putString(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Set<*> ->
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(k, v as MutableSet<String>)
                }
            }
            if (!editor.commit()) {
                AppLog.w(TAG, "迁移旧数据写回失败（数据可能丢失）: $fileName")
            }
        }
        return fresh
    }

    private fun createEncrypted(
        context: Context,
        fileName: String,
        keyAlias: String,
    ): SharedPreferences? = try {
        val masterKey =
            MasterKey
                .Builder(context, keyAlias)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        AppLog.w(TAG, "创建加密存储失败（$fileName）: ${e.javaClass.simpleName}")
        null
    }

    private fun deleteKeyStoreEntry(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private const val TAG = "Polaris-SecurePrefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private const val LEGACY_KEY_ALIAS = "_androidx_security_master_key_"
}

internal class InMemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, Any?> = synchronized(values) { values.toMutableMap() }

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = synchronized(values) { values[key] as? String ?: defValue }

    override fun getStringSet(
        key: String,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = synchronized(values) {
        @Suppress("UNCHECKED_CAST")
        (values[key] as? MutableSet<String>)
            ?: defValues
    }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = synchronized(values) { values[key] as? Int ?: defValue }

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = synchronized(values) { values[key] as? Long ?: defValue }

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = synchronized(values) { values[key] as? Float ?: defValue }

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = synchronized(values) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String): Boolean = synchronized(values) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) synchronized(listeners) { listeners.add(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) synchronized(listeners) { listeners.remove(listener) }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun putStringSet(
            key: String,
            value: MutableSet<String>?,
        ): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }

        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() = applyChanges()

        private fun applyChanges() {
            val changed: Set<String>
            synchronized(values) {
                if (clearAll) values.clear()
                removals.forEach { values.remove(it) }
                pending.forEach { (k, v) -> values[k] = v }
                changed = (removals + pending.keys).toSet()
            }
            if (changed.isEmpty()) return
            val snapshot = synchronized(listeners) { listeners.toList() }
            snapshot.forEach { it.onSharedPreferenceChanged(this@InMemoryPreferences, changed.first()) }
        }
    }
}
