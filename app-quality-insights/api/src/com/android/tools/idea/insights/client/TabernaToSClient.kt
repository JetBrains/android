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

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.gson.Gson

private const val TABERNA_ENDPOINT = "cloudusersettings-pa.googleapis.com"

/** Checks the user setting by using Taberna's HTTP API. */
class TabernaToSClient(
  private val httpTransport: HttpTransport,
  private val authTokenFetcher: () -> String?,
) : ToSClient {

  private val gson: Gson
    get() = Gson()

  private val cache = mutableMapOf<String, Boolean>()

  override fun getUserSetting(rootKey: String, project: String?, subKey: String?): Boolean {

    val authToken = authTokenFetcher() ?: return false
    var path = "v1alpha1/settings/$rootKey"
    project?.let { path += "/projects/$project" }
    subKey?.let { path += "/keys/$subKey" }

    if (cache.getOrDefault(path, false)) {
      return true
    }

    val factory = httpTransport.createRequestFactory()
    val url = GenericUrl("https://$TABERNA_ENDPOINT/$path")
    val request =
      factory.buildGetRequest(url).apply {
        headers = HttpHeaders().apply { authorization = "Bearer $authToken" }
      }

    val response =
      try {
        request.execute().parseAsString()
      } catch (e: Exception) {
        return false
      }

    val parsedResponse =
      try {
        gson.fromJson(response, TabernaResponse::class.java)
      } catch (e: Exception) {
        return false
      }

    return parsedResponse.value?.getOrDefault("boolVal", false)?.also { cache[path] = it } ?: false
  }

  companion object {
    fun create(httpTransport: HttpTransport = NetHttpTransport(), authTokenFetcher: () -> String?) =
      TabernaToSClient(httpTransport, authTokenFetcher)
  }
}

private class TabernaResponse(val value: Map<String, Boolean>?)
