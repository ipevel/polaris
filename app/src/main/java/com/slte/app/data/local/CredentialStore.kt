package com.slte.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密凭证存储：记住登录账号（邮箱 + 密码）。
 *
 * 单槽位：登录哪个账号就覆盖保存哪个；登出后保留，供下次登录预填。
 * 加密密钥由 AndroidKeyStore 管理，设备级安全。
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** 保存登录账号（覆盖上一个账号） */
    fun save(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .apply()
    }

    /** 仅清除已保存的密码，保留邮箱（修改密码后旧密码失效时调用） */
    fun clearPassword() {
        prefs.edit()
            .remove(KEY_PASSWORD)
            .apply()
    }

    /** 读取保存的邮箱，未保存时返回 null */
    fun getSavedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** 读取保存的密码，未保存时返回 null */
    fun getSavedPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun hasSavedEmail(): Boolean = getSavedEmail() != null

    companion object {
        private const val PREFS_NAME = "slte_credential_store"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
    }
}
