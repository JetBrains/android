/*
 * Copyright (C) 2026 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.googleapis.GoogleApiKeyProvider
import com.android.tools.idea.googleapis.GoogleApiKeyProvider.GoogleApi
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

private const val SERVICE_NAME = "AdiClient"

class AdiClient
@JvmOverloads
constructor(private val parentDisposable: Disposable, @TestOnly private val transport: HttpTransport = NetHttpTransport()) {
  private val cache = ConcurrentHashMap<String, Pair<RegistrationState, DevServicesDeprecationData?>>()

  fun reset() = cache.clear()

  fun checkPackageRegistrationStatusAsync(
    packageName: String,
    certificate: ByteArray?,
  ): CompletableFuture<Pair<RegistrationState, DevServicesDeprecationData?>> {
    return parentDisposable
      .createCoroutineScope(Dispatchers.IO)
      .async { checkPackageRegistrationStatus(packageName, certificate) }
      .asCompletableFuture()
  }

  suspend fun checkPackageRegistrationStatus(
    packageName: String,
    certificate: ByteArray?,
  ): Pair<RegistrationState, DevServicesDeprecationData?> {
    val cachedState = cache[packageName]
    if (cachedState != null) {
      return cachedState
    }
    val deprecationData =
      DevServicesDeprecationDataProvider.getInstance().getCurrentDeprecationData(SERVICE_NAME, "Android Developer Verification")
    if (deprecationData.isUnsupported()) {
      return RegistrationState.STUDIO_VERSION_UNSUPPORTED to deprecationData
    }

    val state =
      withContext(Dispatchers.IO) {
        try {
          val urlName = packageName.replace('.', '-')
          val keyParam = certificate?.let { "?certificate_fingerprint=${generateFingerprint(certificate)}" } ?: ""
          val url = "https://androiddeveloperid.googleapis.com/v1/packages/$urlName/packageRegistrationStatus:check$keyParam"

          val response =
            transport
              .createRequestFactory()
              .buildGetRequest(GenericUrl(url))
              .setHeaders(HttpHeaders().set("X-Goog-Api-Key", GoogleApiKeyProvider.getApiKey(GoogleApi.DEVELOPER_ID)))
              .setReadTimeout(10_000)
              .setThrowExceptionOnExecuteError(true)
              .execute()

          val responseBody = response.content.readAllBytes().toString(Charset.defaultCharset())
          Gson().fromJson(responseBody, AdiResponse::class.java).toRegistrationState()
        } catch (e: Exception) {
          thisLogger().warn("Error checking ADI status", e)
          RegistrationState.UNKNOWN
        }
      }

    cache[packageName] = state to null
    return state to null
  }

  private fun generateFingerprint(certificate: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(certificate)
    return digest.joinToString("") { "%02x".format(it) }
  }

  private data class AdiResponse(var name: String? = null, var state: String? = null)

  private fun AdiResponse?.toRegistrationState() =
    when (this?.state) {
      "REGISTERED" -> RegistrationState.REGISTERED
      "NOT_REGISTERED" -> RegistrationState.NOT_REGISTERED
      "REGISTERED_WITH_ANOTHER_CERTIFICATE_FINGERPRINT" -> RegistrationState.BAD_KEY
      else -> RegistrationState.UNKNOWN
    }
}

enum class RegistrationState {
  UNKNOWN,
  REGISTERED,
  NOT_REGISTERED,
  BAD_KEY,
  STUDIO_VERSION_UNSUPPORTED,
}
