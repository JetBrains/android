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
package com.android.tools.idea.gradle.repositories

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.IdeNetworkCacheUtils
import com.android.ide.common.repository.GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY
import com.android.tools.idea.ui.GuiTestingService
import com.google.common.collect.Maps
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

object IdeGoogleMavenRepository : IdeGoogleMavenRepositoryBase(getCacheDir())

/** A [GoogleMavenRepository] that uses IDE mechanisms (including proxy config) to download data. */
open abstract class IdeGoogleMavenRepositoryBase(cacheDir: Path?) : GoogleMavenRepository(cacheDir) {
  @Slow
  override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
    IdeNetworkCacheUtils.readHttpUrlData(url, timeout, lastModified)

  override fun error(throwable: Throwable, message: String?) {
    Logger.getInstance(IdeGoogleMavenRepositoryBase::class.java).warn(message, throwable)
  }

  fun getArtifactsForAll(groupIds: List<String>): Map<String, CompletableFuture<Set<String>>> =
    groupIds.associateWith { groupId ->
      getPackageMap()[groupId]?.artifacts?.thenApply { it.values.map { it.id }.toSet() }
      ?: CompletableFuture.completedFuture(
        emptySet()
      )
    }

  private var packageMap: MutableMap<String, PackageInfoAsync>? = null

  override fun getPackageMap(): Map<String, PackageInfoAsync> {
    if (packageMap == null) {
      val map = Maps.newHashMapWithExpectedSize<String, PackageInfoAsync>(28)
      findData("master-index.xml")?.use { readMasterIndex(it, map){ tag -> PackageInfoAsync(tag) } }
      packageMap = map
    }

    return packageMap!!
  }

  protected inner class PackageInfoAsync(val pkg: String) : GoogleMavenRepository.PackageInfo(pkg) {
    val artifacts: CompletableFuture<Map<String, ArtifactInfo>> by lazy {
      initializeIndex()
    }

    private fun initializeIndex(): CompletableFuture<Map<String, ArtifactInfo>> {
      val futureStream =
        findDataInParallel("${pkg.replace('.', '/')}/group-index.xml")
      return futureStream.thenApply { stream ->
        val map = mutableMapOf<String, ArtifactInfo>()
        stream ?: return@thenApply map
        stream.use {
          readGroupData(stream, map)
          return@thenApply map
        }
      }
    }

    // blocking API
    override fun artifacts(): Set<String> = artifacts.get().values.map { it.id }.toSet()

    // blocking API
    override fun findArtifact(id: String): ArtifactInfo? = artifacts.get()[id]
  }

  /**
   * Execute findData in IO thread.
   * IO thread pool usage is limited to 50 threads. IO has minimum size of 64
   */
  open fun findDataInParallel(
    relative: String,
    treatAsDirectory: Boolean = false
  ): CompletableFuture<InputStream?> {
    return CoroutineScope(Dispatchers.IO.limitedParallelism(50)).async {
      findData(relative, treatAsDirectory)
    }.asCompletableFuture()
  }
}

/** A [GoogleMavenRepository] for only cached data. */
object OfflineIdeGoogleMavenRepository : GoogleMavenRepository(getCacheDir(), useNetwork = false) {
  override fun readUrlData(url: String, timeout: Int, lastModified: Long): ReadUrlDataResult = throw UnsupportedOperationException()

  override fun error(throwable: Throwable, message: String?) {
    Logger.getInstance(OfflineIdeGoogleMavenRepository::class.java).warn(message, throwable)
  }
}

private fun getCacheDir(): Path? {
  if (ApplicationManager.getApplication().isUnitTestMode || GuiTestingService.getInstance().isGuiTestingMode) {
    return null
  }
  return Paths.get(PathManager.getSystemPath()).normalize().resolve(MAVEN_GOOGLE_CACHE_DIR_KEY)
}
