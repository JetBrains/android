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

import com.android.tools.idea.googleapis.GoogleApiKeyProvider
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.test.assertContains
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdiClientTest {
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val applicationRule = ApplicationRule()

  @Before
  fun setUp() {
    val fakeApiKeyProvider =
      object : GoogleApiKeyProvider {
        override fun getApiKey(api: GoogleApiKeyProvider.GoogleApi): String? {
          if (api == GoogleApiKeyProvider.GoogleApi.DEVELOPER_ID) {
            return "fake_developer_id"
          }
          return null
        }
      }
    ExtensionTestUtil.maskExtensions(GoogleApiKeyProvider.EP_NAME, listOf(fakeApiKeyProvider), disposableRule.disposable)
  }

  @Test
  fun testRegistered() = runBlocking {
    val json = """{"state": "REGISTERED"}"""
    val client = AdiClient(disposableRule.disposable, FakeHttpTransport(CompletableFuture.completedFuture(json)))
    val state = client.checkPackageRegistrationStatus("com.example.app", null)
    assertEquals(RegistrationState.REGISTERED, state)
  }

  @Test
  fun testNotRegistered() = runBlocking {
    val json = """{"state": "NOT_REGISTERED"}"""
    val client = AdiClient(disposableRule.disposable, FakeHttpTransport(CompletableFuture.completedFuture(json)))
    val state = client.checkPackageRegistrationStatus("com.example.app", null)
    assertEquals(RegistrationState.NOT_REGISTERED, state)
  }

  @Test
  fun testBadKey() = runBlocking {
    val json = """{"state": "REGISTERED_WITH_ANOTHER_CERTIFICATE_FINGERPRINT"}"""
    val client = AdiClient(disposableRule.disposable, FakeHttpTransport(CompletableFuture.completedFuture(json)))
    val state = client.checkPackageRegistrationStatus("com.example.app", null)
    assertEquals(RegistrationState.BAD_KEY, state)
  }

  @Test
  fun testUnknownState() = runBlocking {
    val json = """{"state": "SOMETHING_ELSE"}"""
    val client = AdiClient(disposableRule.disposable, FakeHttpTransport(CompletableFuture.completedFuture(json)))
    val state = client.checkPackageRegistrationStatus("com.example.app", null)
    assertEquals(RegistrationState.UNKNOWN, state)
  }

  @Test
  fun testNetworkError() = runBlocking {
    val client = AdiClient(disposableRule.disposable, FakeHttpTransport(CompletableFuture.failedFuture(Exception("Network Error"))))
    val state = client.checkPackageRegistrationStatus("com.example.app", null)
    assertEquals(RegistrationState.UNKNOWN, state)
  }

  @Test
  fun testMalformedJson() = runBlocking {
    val json = """{"state": "REGIS"""
    val client = AdiClient(disposableRule.disposable, FakeHttpTransport(CompletableFuture.completedFuture(json)))
    val state = client.checkPackageRegistrationStatus("com.example.app", null)
    assertEquals(RegistrationState.UNKNOWN, state)
  }

  @Test
  fun testFingerprint() = runBlocking {
    val json = """{"state": "REGISTERED"}"""
    val transport = FakeHttpTransport(CompletableFuture.completedFuture(json))
    val client = AdiClient(disposableRule.disposable, transport)
    client.checkPackageRegistrationStatus("com.example.app", byteArrayOf(1, 2, 3))

    // SHA-256 of [1,2,3]
    assertContains(transport.lastRequestedUrl!!, "certificate_fingerprint=039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81")
  }

  private class FakeHttpTransport(val response: Future<String>) : HttpTransport() {
    var lastRequestedUrl: String? = null

    override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
      lastRequestedUrl = url
      return object : LowLevelHttpRequest() {
        var addedHeader = false

        override fun addHeader(name: String, value: String) {
          if (name.equals("X-Goog-Api-Key", true)) {
            addedHeader = true
          }
        }

        override fun execute(): LowLevelHttpResponse {
          if (!addedHeader) {
            throw Exception("Didn't set the api key")
          }
          return object : LowLevelHttpResponse() {
            override fun getContent(): InputStream {
              return response.get().byteInputStream()
            }

            override fun getContentEncoding() = null

            override fun getContentLength() = 0L

            override fun getContentType() = null

            override fun getStatusLine() = null

            override fun getStatusCode() = 200

            override fun getReasonPhrase() = null

            override fun getHeaderCount() = 0

            override fun getHeaderName(idx: Int) = null

            override fun getHeaderValue(idx: Int) = null
          }
        }
      }
    }
  }
}
