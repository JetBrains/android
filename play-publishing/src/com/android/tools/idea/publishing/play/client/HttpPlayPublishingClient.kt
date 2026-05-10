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
import com.android.tools.idea.publishing.play.client.type.AppConfig
import com.android.tools.idea.publishing.play.client.type.Developer
import com.android.tools.idea.publishing.play.client.type.ListDevelopersResponse
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
import kotlin.jvm.java
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BASE_PATH = "androidpublisher/v3"
private const val ONE_MINUTE_IN_MILLIS = 60_000

class HttpPlayPublishingClient(
  private val endPoint: String = StudioFlags.PLAY_PUBLISHING_ENDPOINT.get(),
  private val httpTransport: HttpTransport = NetHttpTransport(),
) : PlayPublishingClient {

  private val url: String
    get() = "https://$endPoint/$BASE_PATH"

  private val requestFactory: HttpRequestFactory
    get() =
      httpTransport.createRequestFactory {
        // TODO: android-merge; the request is unauthenticated here. The credential comes from
        // com.google.gct.login2.GoogleLoginService and fstLoginFeature in tools/vendor/google/login,
        // which this repository does not carry.
        // it.interceptor = GoogleLoginService.instance.getCredential(fstLoginFeature)
        it.parser = JsonObjectParser(GsonFactory.getDefaultInstance())
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
          readTimeout = ONE_MINUTE_IN_MILLIS
          connectTimeout = ONE_MINUTE_IN_MILLIS
        }
      request.execute().parseAs<AppConfig>()
    }

  private inline fun <reified T> HttpResponse.parseAs() = parseAs(T::class.java)
}
