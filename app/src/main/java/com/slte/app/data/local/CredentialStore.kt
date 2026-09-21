package com.slte.app.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore
@Inject
constructor(
    @CredentialPrefs private val prefs: SharedPreferences,
) {

    fun save(
        email: String,
        password: String,
    ) {
        prefs.edit {
            putString(KEY_EMAIL, email)
            putString(KEY_PASSWORD, password)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_EMAIL)
            remove(KEY_PASSWORD)
        }
    }

    fun clearPassword() {
        prefs.edit {
            remove(KEY_PASSWORD)
        }
    }

    fun getSavedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getSavedPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    internal companion object {
        internal const val PREFS_NAME = "polaris_credential_store"
        internal const val KEY_ALIAS = "polaris_credential_master_key"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
    }
}
