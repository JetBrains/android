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

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.waitForCondition
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.concurrency.ThreadingAssertions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.util.concurrent.TimeUnit

/**
 * Tests for the local repository utility class
 */
class RepositoryUrlManagerCachingTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val networkRepo = TestGoogleMavenRepository()
  private val localRepo = TestGoogleMavenRepository()
  private val repositoryUrlManager = RepositoryUrlManager(networkRepo, localRepo, true /* force repository checks */)
  private val fileSystem = createInMemoryFileSystem()

  private class TestGoogleMavenRepository : GoogleMavenRepository() {
    var requestCount: Int = 0
      private set

    override fun findData(relative: String): InputStream? {
      requestCount++
      val result: String =
        when (relative) {
          "master-index.xml" ->
            """<?xml version='1.0' encoding='UTF-8'?>
               <metadata>
                 <com.android.support/>
               </metadata>"""
          "com/android/support/group-index.xml" ->
            """<?xml version='1.0' encoding='UTF-8'?>
               <com.android.support>
                 <support-v4 versions="26.0.2,26.0.2"/>
               </com.android.support>"""
          else -> throw RuntimeException("Unexpected request")
        }

      try {
        return ByteArrayInputStream(result.toByteArray(charset("UTF-8")))
      }
      catch (e: UnsupportedEncodingException) {
        return null
      }
    }

    override fun readUrlData(url: String, timeout: Int, lastModified: Long) = ReadUrlDataResult(null, true)

    override fun error(throwable: Throwable, message: String?) = Unit
  }

  @Test
  @RunsInEdt
  fun calledFromDispatchThread() {
    ThreadingAssertions.assertEventDispatchThread();
    repositoryUrlManager.getLibraryRevision("com.android.support", "support-v4", null, true, fileSystem)

    // When called on the dispatch thread, we return the dependency value from the local cache and post a network request on background.
    assertEquals(2, localRepo.requestCount.toLong())
    waitForCondition(1, TimeUnit.SECONDS) { networkRepo.requestCount == 2 }
  }

  @Test
  fun calledFromWorkerThread() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    repositoryUrlManager.getLibraryRevision("com.android.support", "support-v4", null, true, fileSystem)

    // When called on the worker thread, we return the dependency value from the network only.
    assertEquals(0, localRepo.requestCount.toLong())
    assertEquals(2, networkRepo.requestCount.toLong())
  }
}
