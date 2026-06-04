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
package com.android.tools.idea.publishing.play.client

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.client.type.AppConfig
import com.android.tools.idea.publishing.play.client.type.AppEdit
import com.android.tools.idea.publishing.play.client.type.AppType
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Developer
import com.android.tools.idea.publishing.play.client.type.GoogleApiError
import com.android.tools.idea.publishing.play.client.type.GoogleApiErrorResponse
import com.android.tools.idea.publishing.play.client.type.GoogleApiInnerError
import com.android.tools.idea.publishing.play.client.type.ListAppResponse
import com.android.tools.idea.publishing.play.client.type.ListDevelopersResponse
import com.android.tools.idea.publishing.play.client.type.ListTrackResponse
import com.android.tools.idea.publishing.play.client.type.Release
import com.android.tools.idea.publishing.play.client.type.Status
import com.android.tools.idea.publishing.play.client.type.Track
import com.google.common.truth.Truth.assertThat
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class HttpPlayPublishingClientTest {

  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val flagsRule = FlagRule(StudioFlags.ENABLE_FSTS, true)
  // TODO: android-merge; LoginFeatureRule and LoginUsersRule are in tools/vendor/google/login, which this
  // repository does not carry. The client under test builds its requests without a credential here, so the
  // tests below run without them.
  // private val loginFeatureRule = LoginFeatureRule()
  // private val loginUsersRule = LoginUsersRule()

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(applicationRule).around(disposableRule).around(flagsRule)
  // .around(loginFeatureRule)
  // .around(loginUsersRule)

  private val testJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  @Test
  fun testListAppsSuccess() = runBlocking {
    val client = createClient(content = ListAppResponse(apps = listOf(App(packageName = "com.example.app", displayName = "My App"))))

    val apps = client.listApps()
    assertThat(apps).hasSize(1)
    assertThat(apps[0].packageName).isEqualTo("com.example.app")
    assertThat(apps[0].displayName).isEqualTo("My App")
  }

  @Test
  fun testListAppsHttpErrorThrowsPlayPublishingException() {
    val client =
      createClient(
        statusCode = HttpStatusCode.BadRequest,
        content = GoogleApiErrorResponse(error = GoogleApiError(message = "Bad request")),
      )

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception).hasMessageThat().isEqualTo("Bad request")
  }

  @Test
  fun testListAppsHttpErrorDraftAppDraftReleaseThrowsPlayPublishingException() {
    val client =
      createClient(
        statusCode = HttpStatusCode.BadRequest,
        content = GoogleApiErrorResponse(error = GoogleApiError(message = "Only releases with status draft may be created on draft app.")),
      )

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("No app listing is available for this app. Create one before publishing releases to non-Internal Test Tracks.")
  }

  @Test
  fun testListAppsHttpErrorFailedPreconditionTrackRestrictedThrowsPlayPublishingException() {
    val client =
      createClient(
        statusCode = HttpStatusCode.BadRequest,
        content =
          GoogleApiErrorResponse(
            error =
              GoogleApiError(
                message = "Precondition check failed.",
                errors = listOf(GoogleApiInnerError(debugInfo = "Track beta of app com.test is restricted")),
              )
          ),
      )

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("No app listing is available for this app. Create one before publishing releases to non-Internal Test Tracks.")
  }

  @Test
  fun testListAppsIoErrorThrowsPlayPublishingException() {
    val mockEngine = MockEngine { throw IOException("Network error") }
    val httpClient =
      HttpClient(mockEngine) {
        expectSuccess = true
        install(ContentNegotiation) { json(testJson) }
      }
    val client = HttpPlayPublishingClient(httpClient = httpClient)

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception).hasMessageThat().isEqualTo("Network error")
  }

  @Test
  fun testListDevelopersSuccess() = runBlocking {
    val client = createClient(content = ListDevelopersResponse(developers = listOf(Developer(developerId = 123L, businessName = "Google"))))

    val developers = client.listDevelopers()
    assertThat(developers).hasSize(1)
    assertThat(developers[0].developerId).isEqualTo(123L)
    assertThat(developers[0].businessName).isEqualTo("Google")
  }

  @Test
  fun testListDevelopersNoContent() = runBlocking {
    val client = createClient(statusCode = HttpStatusCode.NoContent)

    val developers = client.listDevelopers()
    assertThat(developers).isEmpty()
  }

  @Test
  fun testCreateAppRecordSuccess() = runBlocking {
    val client =
      createClient(
        content =
          AppConfig(
            packageName = "com.test",
            title = "Test App",
            defaultLanguageCode = "en-US",
            appType = AppType.APP_TYPE_APP,
            paid = false,
          )
      )

    val appConfig = AppConfig(packageName = "com.test", title = "Test App")
    val result = client.createAppRecord(123L, appConfig)
    assertThat(result.packageName).isEqualTo("com.test")
    assertThat(result.title).isEqualTo("Test App")
    assertThat(result.defaultLanguageCode).isEqualTo("en-US")
    assertThat(result.appType).isEqualTo(AppType.APP_TYPE_APP)
    assertThat(result.paid).isFalse()
  }

  @Test
  fun testInsertEditSuccess() = runBlocking {
    val client = createClient(content = AppEdit(id = "edit-123", expiryTimeSeconds = "1000"))

    val result = client.insertEdit("com.test")
    assertThat(result.id).isEqualTo("edit-123")
    assertThat(result.expiryTimeSeconds).isEqualTo("1000")
  }

  @Test
  fun testListEditTracksSuccess(): Unit = runBlocking {
    val client =
      createClient(
        content =
          ListTrackResponse(
            tracks =
              listOf(
                Track(track = "production", releases = listOf(Release(name = "1.0", versionCodes = listOf("1"), status = Status.COMPLETED)))
              )
          )
      )

    val tracks = client.listEditTracks("com.test", "edit-123")
    assertThat(tracks).hasSize(1)
    assertThat(tracks[0].track).isEqualTo("production")
    assertThat(tracks[0].releases).hasSize(1)
    assertThat(tracks[0].releases[0].name).isEqualTo("1.0")
    assertThat(tracks[0].releases[0].versionCodes).containsExactly("1")
  }

  @Test
  fun testUploadBundleBundleSuccess() = runBlocking {
    var requestContentType: ContentType? = null
    val client =
      createClient(
        content = Bundle(versionCode = 1, sha1 = "abc", sha256 = "def"),
        onRequest = { request -> requestContentType = request.body.contentType },
      )

    val tempFile = java.io.File.createTempFile("test", ".aab")
    tempFile.deleteOnExit()

    val result = client.uploadBundle("com.test", "edit-123", tempFile.absolutePath)
    assertThat(result.versionCode).isEqualTo(1)
    assertThat(result.sha1).isEqualTo("abc")
    assertThat(result.sha256).isEqualTo("def")
    assertThat(requestContentType).isEqualTo(ContentType.Application.OctetStream)
  }

  @Test
  fun testCreateReleaseSuccess() = runBlocking {
    val client = createClient(content = "{}")

    // No exception means success since createRelease returns Unit
    client.createRelease("com.test", "edit-123", "Release 1", mapOf("en-US" to "Notes"), 1, "production")
  }

  @Test
  fun testCommitEditSuccess() = runBlocking {
    val client = createClient(content = "{}")

    // No exception means success since commitEdit returns Unit
    client.commitEdit("com.test", "edit-123")
  }

  @Test
  fun testDisposeClosesHttpClient() {
    val client = createClient(content = "{}")
    client.dispose()

    assertThrows(Exception::class.java) { runBlocking { client.listApps() } }
  }

  private fun createClient(
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    content: String = "",
    responseHeaders: Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    onRequest: (HttpRequestData) -> Unit = {},
  ): HttpPlayPublishingClient {
    val mockEngine = MockEngine { request ->
      onRequest(request)
      respond(content, statusCode, responseHeaders)
    }
    val httpClient =
      HttpClient(mockEngine) {
        expectSuccess = true
        install(ContentNegotiation) { json(testJson) }
        install(HttpTimeout)
      }
    return HttpPlayPublishingClient(httpClient = httpClient)
  }

  private inline fun <reified T> createClient(
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    content: T,
    responseHeaders: Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    noinline onRequest: (HttpRequestData) -> Unit = {},
  ): HttpPlayPublishingClient {
    val contentString = testJson.encodeToString(content)
    return createClient(statusCode, contentString, responseHeaders, onRequest)
  }
}
