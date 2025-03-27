/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.util

import com.intellij.util.net.ProxyConfiguration.ProxyAutoConfiguration
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol.HTTP
import com.intellij.util.net.ProxyConfiguration.StaticProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings

class IdeProxyInfo private constructor (val settings: ProxySettings, val credentialStore: ProxyCredentialStore) {
  fun isHttpProxyExplicitlyConfigured() = when(val config = settings.getProxyConfiguration()) {
    is StaticProxyConfiguration -> config.protocol == HTTP // no SOCKS for us.  Poor Dobby.
    is ProxyAutoConfiguration -> config.pacUrl.toString().isNotEmpty()
    else -> false // we might still be using a proxy but only through JVM variables.
  }

  companion object {
    private val INSTANCE by lazy { IdeProxyInfo(ProxySettings.getInstance(), ProxyCredentialStore.getInstance()) }

    @JvmStatic
    fun getInstance() = INSTANCE
  }
}