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
package com.android.tools.idea.insights.client

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TabernaToSClientTest {

  @Test
  fun `test taberna client caches path specific response`() = runBlocking {
    val fakeHttpTransport = createHttpTransport(true)
    val client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    assertThat(client.getUserSetting("rootKey", "project", "subKey")).isTrue()
    // Call getUserSetting once again to make sure it does not send a request.
    assertThat(client.getUserSetting("rootKey", "project", "subKey")).isTrue()

    // This request would fail cache and make a call.
    assertThat(client.getUserSetting("rootKey", "project", "otherKey")).isTrue()

    assertThat(fakeHttpTransport.counter).isEqualTo(2)
  }

  @Test
  fun `test taberna client resends request when not accepted`() = runBlocking {
    val fakeHttpTransport = createHttpTransport(false)
    val client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    assertThat(client.getUserSetting("rootKey", "project", "subKey")).isFalse()
    // Call getUserSetting once again to make sure it sends a request again
    assertThat(client.getUserSetting("rootKey", "project", "subKey")).isFalse()
    assertThat(fakeHttpTransport.counter).isEqualTo(2)
  }

  @Test
  fun `test taberna client returns false if response does not contain boolVal`() = runBlocking {
    val fakeHttpTransport = createHttpTransport(null)
    val client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    assertThat(client.getUserSetting("rootKey", "project", "subKey")).isFalse()
    // Call getUserSetting once again to make sure it sends a request again
    assertThat(client.getUserSetting("rootKey", "project", "subKey")).isFalse()
    assertThat(fakeHttpTransport.counter).isEqualTo(2)
  }

  @Test
  fun `test taberna client returns false if the value does not exist`() = runBlocking {
    // Taberna throws exception if the setting does not exist
    val fakeHttpTransport = createHttpTransport(response = null, throwException = true)
    val client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    assertThat(client.getUserSetting("rootKey", "project", "subKey")).isFalse()
    assertThat(fakeHttpTransport.counter).isEqualTo(1)
  }

  @Test
  fun `test expectedUrl`() = runBlocking {
    var fakeHttpTransport = createHttpTransport()
    var client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    client.getUserSetting("rootKey", "project", "subKey")
    assertThat(fakeHttpTransport.url)
      .isEqualTo(
        "https://cloudusersettings-pa.googleapis.com/v1alpha1/settings/rootKey/projects/project/keys/subKey"
      )

    fakeHttpTransport = createHttpTransport()
    client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    client.getUserSetting("rootKey")
    assertThat(fakeHttpTransport.url)
      .isEqualTo("https://cloudusersettings-pa.googleapis.com/v1alpha1/settings/rootKey")

    fakeHttpTransport = createHttpTransport()
    client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    client.getUserSetting("rootKey", null, "subKey")
    assertThat(fakeHttpTransport.url)
      .isEqualTo(
        "https://cloudusersettings-pa.googleapis.com/v1alpha1/settings/rootKey/keys/subKey"
      )

    fakeHttpTransport = createHttpTransport()
    client = TabernaToSClient.create(fakeHttpTransport) { "authToken" }

    client.getUserSetting("rootKey", "project", null)
    assertThat(fakeHttpTransport.url)
      .isEqualTo(
        "https://cloudusersettings-pa.googleapis.com/v1alpha1/settings/rootKey/projects/project"
      )
  }

  private fun createHttpTransport(response: Boolean? = null, throwException: Boolean = false) =
    object : HttpTransport() {
      private val request = createHttpRequest(response, throwException)

      var counter: Int = 0
        private set

      var url: String
        get() = request.url
        private set(value) {
          request.url = value
        }

      override fun buildRequest(p0: String, p1: String): LowLevelHttpRequest {
        counter++
        return request.also { url = p1 }
      }
    }

  private fun createHttpRequest(response: Boolean?, throwException: Boolean) =
    object : LowLevelHttpRequest() {
      private val responseFormat =
        """
        {
          "key": {
            "setting": "FIREBASE_PROJECT_PROMOS_DISMISSED",
            "subkey": "helios_terms_acceptance_2024_01_18",
            "projectNumber": "12345678",
            "projectId": "project-id"
          },
          "version": "987654321"%s
        }
      """
          .trimIndent()

      private val cache = mutableMapOf<String, Boolean>()

      var url: String = ""

      override fun addHeader(p0: String?, p1: String?) {
        if (p0 == "Authorization") {
          assertThat(p1).isEqualTo("Bearer authToken")
        }
      }

      override fun execute(): LowLevelHttpResponse {
        if (throwException || cache.getOrDefault(url, false)) throw IOException()
        val formattedResponse =
          response?.let {
            String.format(responseFormat, ",\n\"value\": { \"boolVal\": $response }")
          } ?: String.format(responseFormat, "")
        return MockLowLevelHttpResponse().apply {
          content = ByteArrayInputStream(formattedResponse.toByteArray())
        }
      }
    }
}
