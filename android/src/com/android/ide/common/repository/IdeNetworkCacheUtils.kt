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
package com.android.ide.common.repository

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.NetworkCache.ReadUrlDataResult
import com.intellij.util.io.HttpRequests
import java.net.HttpURLConnection
import java.net.URL

class IdeNetworkCacheUtils {
  companion object {
    @Slow
    fun readHttpUrlData(url: String, timeout: Int, lastModified: Long, mimeType: String? = null): ReadUrlDataResult = HttpRequests
      .request(URL(url).toExternalForm())
      .connectTimeout(timeout)
      .readTimeout(timeout)
      .accept(mimeType)
      .tuner { c -> c.ifModifiedSince = lastModified }
      .connect { r ->
        r.connection.let { connection ->
          when (connection) {
            is HttpURLConnection -> if (connection.responseCode == 304) return@let ReadUrlDataResult(null, false)
          }
          ReadUrlDataResult(r.readBytes(null), true)
        }
      }
  }
}

