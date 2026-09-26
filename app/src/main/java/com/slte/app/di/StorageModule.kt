// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.di

import android.content.Context
import android.content.SharedPreferences
import com.slte.app.data.local.CredentialPrefs
import com.slte.app.data.local.CredentialStore
import com.slte.app.data.local.DnsFailurePrefs
import com.slte.app.data.local.DnsFailureStore
import com.slte.app.data.local.NodesSectionPrefs
import com.slte.app.data.local.NodesSectionStore
import com.slte.app.data.local.SecurePreferences
import com.slte.app.data.local.SessionPrefs
import com.slte.app.data.local.SessionStore
import com.slte.app.data.local.SiteInfoPrefs
import com.slte.app.data.local.SiteInfoStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    @SessionPrefs
    fun provideSessionPrefs(
        @ApplicationContext context: Context,
    ): SharedPreferences = SecurePreferences.create(context, SessionStore.PREFS_NAME, SessionStore.KEY_ALIAS)

    @Provides
    @Singleton
    @CredentialPrefs
    fun provideCredentialPrefs(
        @ApplicationContext context: Context,
    ): SharedPreferences = SecurePreferences.create(context, CredentialStore.PREFS_NAME, CredentialStore.KEY_ALIAS)

    @Provides
    @Singleton
    @SiteInfoPrefs
    fun provideSiteInfoPrefs(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(SiteInfoStore.PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @NodesSectionPrefs
    fun provideNodesSectionPrefs(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(NodesSectionStore.PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @DnsFailurePrefs
    fun provideDnsFailurePrefs(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(DnsFailureStore.PREFS_NAME, Context.MODE_PRIVATE)
}
