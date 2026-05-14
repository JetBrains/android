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
import com.android.tools.idea.publishing.play.client.type.Apk
import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.client.type.AppConfig
import com.android.tools.idea.publishing.play.client.type.AppEdit
import com.android.tools.idea.publishing.play.client.type.Artifact
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Developer
import com.android.tools.idea.publishing.play.client.type.ListAppResponse
import com.android.tools.idea.publishing.play.client.type.ListDevelopersResponse
import com.android.tools.idea.publishing.play.client.type.ListTrackResponse
import com.android.tools.idea.publishing.play.client.type.LocalizedText
import com.android.tools.idea.publishing.play.client.type.Release
import com.android.tools.idea.publishing.play.client.type.Status
import com.android.tools.idea.publishing.play.client.type.Track
import com.google.api.client.http.EmptyContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.json.JsonHttpContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.gson.GsonFactory
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.GoogleLoginService
// import com.google.gct.login2.fstLoginFeature
import java.io.File
import kotlin.jvm.java
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BASE_PATH = "androidpublisher/v3"

class HttpPlayPublishingClient(
  private val endPoint: String = StudioFlags.PLAY_PUBLISHING_ENDPOINT.get(),
  private val httpTransport: HttpTransport = NetHttpTransport(),
) : PlayPublishingClient {

  private val url: String
    get() = "https://$endPoint/$BASE_PATH"

  private val uploadUrl: String
    get() = "https://$endPoint/upload/$BASE_PATH"

  private val requestFactory: HttpRequestFactory
    get() =
      httpTransport.createRequestFactory {
        // TODO: android-merge; the request is unauthenticated here. The credential comes from
        // com.google.gct.login2.GoogleLoginService and fstLoginFeature in tools/vendor/google/login,
        // which this repository does not carry.
        // it.interceptor = GoogleLoginService.instance.getCredential(fstLoginFeature)
        it.parser = JsonObjectParser(GsonFactory.getDefaultInstance())
      }

  override suspend fun listApps(): List<App> =
    withContext(Dispatchers.IO) {
      val allApps = mutableListOf<App>()
      var nextPageToken: String? = null

      do {
        val listAppUrl = GenericUrl("https://${StudioFlags.PLAY_VITALS_GRPC_SERVER.get()}/v1beta1/apps:search")
        if (nextPageToken != null) {
          listAppUrl.set("pageToken", nextPageToken)
        }
        val request = requestFactory.buildGetRequest(listAppUrl)
        val response = request.execute()
        val listAppResponse = response.parseAs<ListAppResponse>()
        allApps.addAll(listAppResponse.apps)
        nextPageToken = listAppResponse.nextPageToken.takeIf { it.isNotEmpty() }
      } while (nextPageToken != null)
      allApps
    }

  override suspend fun listDevelopers(): List<Developer> =
    withContext(Dispatchers.IO) {
      val listDeveloperUrl = GenericUrl("$url/developers")
      val request = requestFactory.buildGetRequest(listDeveloperUrl)
      val response = request.execute()
      response.parseAs<ListDevelopersResponse>().developers
    }

  override suspend fun createAppRecord(developerId: Long, appConfig: AppConfig): AppConfig =
    withContext(Dispatchers.IO) {
      val createAppRecordUrl = GenericUrl("$url/developers/$developerId/appsmanagement")
      val content = JsonHttpContent(GsonFactory.getDefaultInstance(), appConfig)
      val request =
        requestFactory.buildPostRequest(createAppRecordUrl, content).apply {
          val timeout = 1.minutes.inWholeMilliseconds.toInt()
          connectTimeout = timeout
          readTimeout = timeout
        }
      request.execute().parseAs<AppConfig>()
    }

  override suspend fun insertEdit(packageName: String): AppEdit =
    withContext(Dispatchers.IO) {
      val insertUrl = GenericUrl("$url/applications/$packageName/edits")
      val request = requestFactory.buildPostRequest(insertUrl, EmptyContent())
      request.execute().parseAs<AppEdit>()
    }

  override suspend fun listEditTracks(packageName: String, editId: String): List<Track> =
    withContext(Dispatchers.IO) {
      val listEditTrackUrl = GenericUrl("$url/applications/$packageName/edits/$editId/tracks")
      val request = requestFactory.buildGetRequest(listEditTrackUrl)
      request.execute().parseAs<ListTrackResponse>().tracks
    }

  override suspend fun uploadArtifact(packageName: String, editId: String, artifactPath: String, isBundle: Boolean): Artifact =
    withContext(Dispatchers.IO) {
      val finalPath =
        if (isBundle) {
          "bundles"
        } else {
          "apks"
        }
      val uploadArtifactUrl = GenericUrl("$uploadUrl/applications/$packageName/edits/$editId/$finalPath")
      val file = File(artifactPath)
      val content = FileContent("application/octet-stream", file)
      val request =
        requestFactory.buildPostRequest(uploadArtifactUrl, content).apply {
          // Uploading apps may take longer
          val timeout = 10.minutes.inWholeMilliseconds.toInt()
          connectTimeout = timeout
          readTimeout = timeout
        }
      val response = request.execute()
      if (isBundle) {
        response.parseAs<Bundle>()
      } else {
        response.parseAs<Apk>()
      }
    }

  override suspend fun createRelease(
    packageName: String,
    editId: String,
    releaseName: String,
    releaseNotes: Map<String, String>,
    versionCode: Int,
    trackId: String,
  ) =
    withContext(Dispatchers.IO) {
      val updateTrackUrl = GenericUrl("$url/applications/$packageName/edits/$editId/tracks/$trackId")
      val release =
        Release(
          name = releaseName,
          versionCodes = listOf(versionCode.toString()),
          releaseNotes = releaseNotes.map { LocalizedText(it.key, it.value) },
          status = Status.COMPLETED,
        )
      val track = Track(track = trackId, releases = listOf(release))
      val content = JsonHttpContent(GsonFactory.getDefaultInstance(), track)
      val request = requestFactory.buildPutRequest(updateTrackUrl, content)
      request.execute().ignore()
    }

  override suspend fun commitEdit(packageName: String, editId: String) =
    withContext(Dispatchers.IO) {
      val commitUrl = GenericUrl("$url/applications/$packageName/edits/$editId:commit")
      val request = requestFactory.buildPostRequest(commitUrl, EmptyContent())
      request.execute().ignore()
    }

  private inline fun <reified T> HttpResponse.parseAs() = parseAs(T::class.java)
}
