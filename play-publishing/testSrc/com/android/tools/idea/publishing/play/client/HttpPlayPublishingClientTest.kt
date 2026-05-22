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
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.common.truth.Truth.assertThat
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import java.io.IOException
import kotlinx.coroutines.runBlocking
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

  @Test
  fun testListAppsSuccess() = runBlocking {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
          MockLowLevelHttpResponse()
            .setStatusCode(200)
            .setContent("""{"apps": [{"packageName": "com.example.app", "displayName": "My App"}], "nextPageToken": ""}""")
        )
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val apps = client.listApps()
    assertThat(apps).hasSize(1)
    assertThat(apps[0].packageName).isEqualTo("com.example.app")
    assertThat(apps[0].displayName).isEqualTo("My App")
  }

  @Test
  fun testListAppsHttpErrorThrowsPlayPublishingException() {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(MockLowLevelHttpResponse().setStatusCode(400).setContent("""{"error": {"message": "Bad request"}}"""))
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception).hasMessageThat().isEqualTo("Bad request")
  }

  @Test
  fun testListAppsHttpErrorDraftAppDraftReleaseThrowsPlayPublishingException() {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
          MockLowLevelHttpResponse()
            .setStatusCode(400)
            .setContent("""{"error": {"message": "Only releases with status draft may be created on draft app."}}""")
        )
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("No app listing is available for this app. Create one before publishing releases to non-Internal Test Tracks.")
  }

  @Test
  fun testListAppsHttpErrorFailedPreconditionTrackRestrictedThrowsPlayPublishingException() {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
          MockLowLevelHttpResponse()
            .setStatusCode(400)
            .setContent(
              """{"error": {"message": "Precondition check failed.", "errors": [{"debugInfo": "Track beta of app com.test is restricted"}]}}"""
            )
        )
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception)
      .hasMessageThat()
      .isEqualTo("No app listing is available for this app. Create one before publishing releases to non-Internal Test Tracks.")
  }

  @Test
  fun testListAppsIoErrorThrowsPlayPublishingException() {
    val transport =
      object : MockHttpTransport() {
        override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
          return object : MockLowLevelHttpRequest() {
            override fun execute(): LowLevelHttpResponse {
              throw IOException("Network error")
            }
          }
        }
      }

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val exception = assertThrows(PlayPublishingException::class.java) { runBlocking { client.listApps() } }

    assertThat(exception).hasMessageThat().isEqualTo("Network error")
  }

  @Test
  fun testListDevelopersSuccess() = runBlocking {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
          MockLowLevelHttpResponse().setStatusCode(200).setContent("""{"developers": [{"developerId": 123, "businessName": "Google"}]}""")
        )
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val developers = client.listDevelopers()
    assertThat(developers).hasSize(1)
    assertThat(developers[0].developerId).isEqualTo(123L)
    assertThat(developers[0].businessName).isEqualTo("Google")
  }

  @Test
  fun testListDevelopersNoContent() = runBlocking {
    val transport = MockHttpTransport.Builder().setLowLevelHttpResponse(MockLowLevelHttpResponse().setStatusCode(204)).build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val developers = client.listDevelopers()
    assertThat(developers).isEmpty()
  }

  @Test
  fun testCreateAppRecordSuccess() = runBlocking {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
          MockLowLevelHttpResponse()
            .setStatusCode(200)
            .setContent(
              """{"packageName": "com.test", "title": "Test App", "defaultLanguageCode": "en-US", "appType": "APP_TYPE_APP", "paid": false}"""
            )
        )
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val appConfig = com.android.tools.idea.publishing.play.client.type.AppConfig(packageName = "com.test", title = "Test App")
    val result = client.createAppRecord(123L, appConfig)
    assertThat(result.packageName).isEqualTo("com.test")
    assertThat(result.title).isEqualTo("Test App")
    assertThat(result.defaultLanguageCode).isEqualTo("en-US")
    assertThat(result.appType).isEqualTo(com.android.tools.idea.publishing.play.client.type.AppType.APP_TYPE_APP)
    assertThat(result.paid).isFalse()
  }

  @Test
  fun testInsertEditSuccess() = runBlocking {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
          MockLowLevelHttpResponse().setStatusCode(200).setContent("""{"id": "edit-123", "expiryTimeSeconds": "1000"}""")
        )
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val result = client.insertEdit("com.test")
    assertThat(result.id).isEqualTo("edit-123")
    assertThat(result.expiryTimeSeconds).isEqualTo("1000")
  }

  @Test
  fun testListEditTracksSuccess() =
    runBlocking<Unit> {
      val transport =
        MockHttpTransport.Builder()
          .setLowLevelHttpResponse(
            MockLowLevelHttpResponse()
              .setStatusCode(200)
              .setContent(
                """{"tracks": [{"track": "production", "releases": [{"name": "1.0", "versionCodes": ["1"], "status": "completed"}]}]}"""
              )
          )
          .build()

      val client = HttpPlayPublishingClient(httpTransport = transport)

      val tracks = client.listEditTracks("com.test", "edit-123")
      assertThat(tracks).hasSize(1)
      assertThat(tracks[0].track).isEqualTo("production")
      assertThat(tracks[0].releases).hasSize(1)
      assertThat(tracks[0].releases[0].name).isEqualTo("1.0")
      assertThat(tracks[0].releases[0].versionCodes).containsExactly("1")
    }

  @Test
  fun testUploadBundleBundleSuccess() = runBlocking {
    val transport =
      MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
          MockLowLevelHttpResponse().setStatusCode(200).setContent("""{"versionCode": 1, "sha1": "abc", "sha256": "def"}""")
        )
        .build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    val tempFile = java.io.File.createTempFile("test", ".aab")
    tempFile.deleteOnExit()

    val result = client.uploadBundle("com.test", "edit-123", tempFile.absolutePath)
    assertThat(result.versionCode).isEqualTo(1)
    assertThat(result.sha1).isEqualTo("abc")
    assertThat(result.sha256).isEqualTo("def")
  }

  @Test
  fun testCreateReleaseSuccess() = runBlocking {
    val transport =
      MockHttpTransport.Builder().setLowLevelHttpResponse(MockLowLevelHttpResponse().setStatusCode(200).setContent("""{}""")).build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    // No exception means success since createRelease returns Unit
    client.createRelease("com.test", "edit-123", "Release 1", mapOf("en-US" to "Notes"), 1, "production")
  }

  @Test
  fun testCommitEditSuccess() = runBlocking {
    val transport =
      MockHttpTransport.Builder().setLowLevelHttpResponse(MockLowLevelHttpResponse().setStatusCode(200).setContent("""{}""")).build()

    val client = HttpPlayPublishingClient(httpTransport = transport)

    // No exception means success since commitEdit returns Unit
    client.commitEdit("com.test", "edit-123")
  }
}
