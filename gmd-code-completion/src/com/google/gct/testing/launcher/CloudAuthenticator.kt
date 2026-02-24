/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.testing.launcher

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.testing.Testing
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.LoginFeature
import com.google.gct.testing.CloudTestingUtils
import com.google.services.firebase.FirebaseLoginFeature
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val APPLICATION_NAME = "GCTL"

@Service
class CloudAuthenticator(scope: CoroutineScope) {
  /** Global instance of the HTTP transport. */
  private var myHttpTransport: HttpTransport? = null
    get() = field ?: NetHttpTransport().also { field = it }

  private var myTest: Testing? = null
  private var myLastDiscoveryServiceInvocationTimestamp: Long = -1

  init {
    scope.launch {
      fun reset() {
        myHttpTransport = null
        myTest = null
      }

      service<GoogleLoginService>().activeUserFlow.collect { reset() }
    }
  }

  private val firebaseFeature = LoginFeature.feature<FirebaseLoginFeature>()

  /** Get a test client pointing to the default (prod) backend. */
  val test: Testing
    get() = getTest(null)

  /** Get a test client pointing to the given backend. */
  private fun getTest(endpoint: String?): Testing {
    return myTest
      ?: Testing.Builder(myHttpTransport, GsonFactory.getDefaultInstance(), firebaseFeature.credential())
        .setApplicationName(APPLICATION_NAME)
        .apply {
          if (endpoint != null) {
            setRootUrl(endpoint)
          }
        }
        .build()
        .also { myTest = it }
  }

  /** Get the [AndroidDeviceCatalog] for the given FTL `endpoint`. */
  @Throws(IOException::class)
  fun getAndroidDeviceCatalogForEnvironment(endpoint: String?, gcpProject: String?): AndroidDeviceCatalog {
    // TODO: Move com.google.services.firebase.directaccess.client.CloudClient to a common place and use it here
    val currentTimestamp = System.currentTimeMillis()
    try {
      val getter = getTest(endpoint).testEnvironmentCatalog()["ANDROID"]
      getter.setProjectId(gcpProject)
      getter.requestHeaders["X-Goog-User-Project"] = gcpProject
      val catalog = getter.execute().androidDeviceCatalog
      if (
        catalog.versions.isEmpty() ||
          catalog.models.isEmpty() ||
          catalog.runtimeConfiguration.locales.isEmpty() ||
          catalog.runtimeConfiguration.orientations.isEmpty()
      ) {
        showDeviceCatalogError("Android device catalog is empty for some dimensions", currentTimestamp)
      }
      return catalog
    } finally {
      myLastDiscoveryServiceInvocationTimestamp = currentTimestamp
    }
  }

  /** Get the [AndroidDeviceCatalog] for the default (prod) FTL backend. */
  val androidDeviceCatalog: AndroidDeviceCatalog?
    get() {
      try {
        return getAndroidDeviceCatalogForEnvironment(null, null)
      } catch (e: IOException) {
        showDeviceCatalogError(
          """
  Exception while getting Android device catalog

  ${e.message}
  """
            .trimIndent(),
          System.currentTimeMillis(),
        )
        return null
      }
    }

  private fun showDeviceCatalogError(errorMessageSuffix: String, currentTimestamp: Long) {
    // The error should be reported just once per burst of invocations.
    if (currentTimestamp - myLastDiscoveryServiceInvocationTimestamp > 1000L) { // If more than a second has passed.
      CloudTestingUtils.showErrorMessage(
        null,
        "Error retrieving android device catalog",
        "Failed to retrieve available firebase devices! Please try again later.\n$errorMessageSuffix",
      )
    }
  }

  companion object {

    @JvmStatic
    val instance: CloudAuthenticator
      get() = service<CloudAuthenticator>()
  }
}
