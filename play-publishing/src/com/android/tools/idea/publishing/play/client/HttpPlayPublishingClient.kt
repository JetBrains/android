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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.client.type.AppConfig
import com.android.tools.idea.publishing.play.client.type.AppEdit
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Developer
import com.android.tools.idea.publishing.play.client.type.GoogleApiInnerError
import com.android.tools.idea.publishing.play.client.type.ListAppResponse
import com.android.tools.idea.publishing.play.client.type.ListDevelopersResponse
import com.android.tools.idea.publishing.play.client.type.ListTrackResponse
import com.android.tools.idea.publishing.play.client.type.LocalizedText
import com.android.tools.idea.publishing.play.client.type.Release
import com.android.tools.idea.publishing.play.client.type.Status
import com.android.tools.idea.publishing.play.client.type.Track
import com.android.tools.idea.publishing.play.client.type.parseGoogleApiError
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.fstLoginFeature
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.text.nullize
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.content.LocalFileContent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val BASE_PATH = "androidpublisher/v3"

private const val NO_APP_LISTING_CORRECTION_MESSAGE =
  "No app listing is available for this app. Create one before publishing releases to non-Internal Test Tracks."
private const val DRAFT_APP_DRAFT_RELEASE = "Only releases with status draft may be created on draft app."
private const val FAILED_PRECONDITION = "Precondition check failed."
private val TRACK_RESTRICTED_REGEX = "Track .* of app .* is restricted".toRegex()

class HttpPlayPublishingClient(
  private val endPoint: String = StudioFlags.PLAY_PUBLISHING_ENDPOINT.get(),
  private val httpClient: HttpClient = defaultHttpClient(),
) : PlayPublishingClient, Disposable {

  private val url: String
    get() = "https://$endPoint/$BASE_PATH"

  private val uploadUrl: String
    get() = "https://$endPoint/upload/$BASE_PATH"

  private val logger: Logger
    get() = thisLogger()

  override suspend fun listApps(): List<App> = runPublishingTask {
    val allApps = mutableListOf<App>()
    var nextPageToken: String? = null

    do {
      val response =
        httpClient.get("https://${StudioFlags.PLAY_VITALS_GRPC_SERVER.get()}/v1beta1/apps:search") {
          if (nextPageToken != null) {
            parameter("pageToken", nextPageToken)
          }
        }
      val listAppResponse = response.body<ListAppResponse>()
      allApps.addAll(listAppResponse.apps)
      nextPageToken = listAppResponse.nextPageToken.nullize()
    } while (nextPageToken != null)
    allApps
  }

  override suspend fun listDevelopers(): List<Developer> = runPublishingTask {
    val response = httpClient.get("$url/developers")

    // Temporary workaround for b/513706971
    if (response.status == HttpStatusCode.NoContent) {
      emptyList()
    } else {
      response.body<ListDevelopersResponse>().developers
    }
  }

  override suspend fun createAppRecord(developerId: Long, appConfig: AppConfig): AppConfig = runPublishingTask {
    val response =
      httpClient.post("$url/developers/$developerId/appsmanagement") {
        contentType(ContentType.Application.Json)
        setBody(appConfig)
        timeout {
          val timeoutMs = 1.minutes.inWholeMilliseconds
          requestTimeoutMillis = timeoutMs
          connectTimeoutMillis = timeoutMs
        }
      }
    response.body<AppConfig>()
  }

  override suspend fun insertEdit(packageName: String): AppEdit = runPublishingTask {
    val response = httpClient.post("$url/applications/$packageName/edits")
    response.body<AppEdit>()
  }

  override suspend fun listEditTracks(packageName: String, editId: String): List<Track> = runPublishingTask {
    val response = httpClient.get("$url/applications/$packageName/edits/$editId/tracks")
    response.body<ListTrackResponse>().tracks
  }

  override suspend fun uploadBundle(packageName: String, editId: String, bundlePath: String): Bundle = runPublishingTask {
    val file = File(bundlePath)
    val response =
      httpClient.post("$uploadUrl/applications/$packageName/edits/$editId/bundles") {
        contentType(ContentType.Application.OctetStream)
        setBody(LocalFileContent(file, ContentType.Application.OctetStream))
        timeout {
          val timeoutMs = 10.minutes.inWholeMilliseconds
          requestTimeoutMillis = timeoutMs
          connectTimeoutMillis = timeoutMs
        }
      }
    response.body<Bundle>()
  }

  override suspend fun createRelease(
    packageName: String,
    editId: String,
    releaseName: String,
    releaseNotes: Map<String, String>,
    versionCode: Int,
    trackId: String,
  ) = runPublishingTask {
    val release =
      Release(
        name = releaseName,
        versionCodes = listOf(versionCode.toString()),
        releaseNotes = releaseNotes.map { LocalizedText(it.key, it.value) },
        status = Status.COMPLETED,
      )
    val track = Track(track = trackId, releases = listOf(release))
    val response =
      httpClient.put("$url/applications/$packageName/edits/$editId/tracks/$trackId") {
        contentType(ContentType.Application.Json)
        setBody(track)
      }
    response.body<Unit>()
  }

  override suspend fun commitEdit(packageName: String, editId: String) = runPublishingTask {
    val response = httpClient.post("$url/applications/$packageName/edits/$editId:commit")
    response.body<Unit>()
  }

  private suspend fun <T> runPublishingTask(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.IO) {
      try {
        block()
      } catch (e: ResponseException) {
        val content =
          try {
            e.response.bodyAsText()
          } catch (_: Exception) {
            ""
          }
        val googleError = parseGoogleApiError(content)
        val message = googleError?.message ?: e.message ?: "Unknown error"
        logger.warn("Play Publishing API error: $message", e)
        throw PlayPublishingException(maybeCorrectExceptionMessage(message, googleError?.errors), e)
      } catch (e: CancellationException) {
        throw e
      } catch (e: IOException) {
        logger.warn("Play Publishing network error: ${e.message}", e)
        throw PlayPublishingException(e.message ?: "Unknown error", e)
      }
    }

  private fun maybeCorrectExceptionMessage(message: String, errors: List<GoogleApiInnerError>?): String =
    when {
      message.contains(DRAFT_APP_DRAFT_RELEASE) -> {
        NO_APP_LISTING_CORRECTION_MESSAGE
      }
      message.contains(FAILED_PRECONDITION) && (errors?.containsRestrictedTrackDebugInfo() == true) -> {
        NO_APP_LISTING_CORRECTION_MESSAGE
      }
      else -> message
    }

  private fun List<GoogleApiInnerError>.containsRestrictedTrackDebugInfo() = any { it.debugInfo?.contains(TRACK_RESTRICTED_REGEX) == true }

  override fun dispose() {
    httpClient.close()
  }
}

private fun defaultHttpClient(): HttpClient {
  return HttpClient {
    expectSuccess = true
    install(ContentNegotiation) {
      json(
        Json {
          ignoreUnknownKeys = true
          coerceInputValues = true
        }
      )
    }
    install(HttpTimeout)
    // TODO: android-merge; the request is unauthenticated here. The access token comes from
    // com.google.gct.login2.fstLoginFeature in tools/vendor/google/login, which this repository does not carry.
    // defaultRequest {
    //   val accessToken = fstLoginFeature.oAuthToken()
    //   if (accessToken != null) {
    //     header("Authorization", "Bearer $accessToken")
    //   }
    // }
  }
}
