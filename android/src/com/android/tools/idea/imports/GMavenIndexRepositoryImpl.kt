/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.NetworkCache
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import java.io.File
import java.io.InputStream
import java.net.URL


/** Key used in cache directories to locate the gmaven.index network cache. */
const val GMAVEN_INDEX_CACHE_DIR_KEY = "gmaven.index"
private const val BASE_URL = "https://dl.google.com/android/studio/gmaven/index/test/"

/**
 * An implementation of [GMavenIndexRepository] to provide data about the GMaven indices on [BASE_URL].
 */
class GMavenIndexRepositoryImpl(cacheDir: File?) : GMavenIndexRepository, NetworkCache(BASE_URL, GMAVEN_INDEX_CACHE_DIR_KEY, cacheDir) {
  /**
   * Reads the data by given relative URL to the base URL.
   *
   * Either it's from valid cached data, or it's from network.
   */
  override fun fetchIndex(relative: String): InputStream? {
    return findData(relative)
  }

  /**
   * Reads the given query URL in, with the given time out, and returns the bytes found.
   */
  @Slow
  override fun readUrlData(url: String, timeout: Int): ByteArray? = HttpRequests
    .request(URL(url).toExternalForm())
    .connectTimeout(timeout)
    .readTimeout(timeout)
    .readBytes(null)

  override fun readDefaultData(relative: String): InputStream? = null

  override fun error(throwable: Throwable, message: String?) {
    Logger.getInstance(GMavenIndexRepositoryImpl::class.java).warn(message, throwable)
  }
}

/**
 * Provides service to fetch GMaven index.
 */
interface GMavenIndexRepository {
  /**
   * Reads the data by given relative URL to the base URL.
   */
  fun fetchIndex(relative: String): InputStream?
}