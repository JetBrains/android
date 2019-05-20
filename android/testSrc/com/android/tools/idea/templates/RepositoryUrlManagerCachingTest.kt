/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates

import org.junit.Assert.assertEquals

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.repository.testframework.MockFileOp
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.FutureResult
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.util.concurrent.Future
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for the local repository utility class
 */
class RepositoryUrlManagerCachingTest {

  private lateinit var disposable: Disposable
  private lateinit var mockApplication: TestMockApplication
  private val networkRepo = TestGoogleMavenRepository()
  private val localRepo = TestGoogleMavenRepository()
  private val repositoryUrlManager = RepositoryUrlManager(networkRepo, localRepo, true /* force repository checks */)
  private val fileOp = MockFileOp()

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

    override fun readUrlData(url: String, timeout: Int): ByteArray? = null

    override fun error(throwable: Throwable, message: String?) = Unit
  }

  private class TestMockApplication(parentDisposable: Disposable) : MockApplication(parentDisposable) {

    private var isDispatchThread: Boolean = false

    fun setDispatchThread(isDispatchThread: Boolean) {
      this.isDispatchThread = isDispatchThread
    }

    override fun isDispatchThread(): Boolean = isDispatchThread

    override fun executeOnPooledThread(action: Runnable): Future<*> {
      // For this test we want to run on the same thread. We are just checking the method is called.
      action.run()
      return FutureResult<Any>()
    }
  }

  @Before
  fun setUp() {
    disposable = Disposer.newDisposable()
    mockApplication = TestMockApplication(disposable)
    ApplicationManager.setApplication(mockApplication, disposable)
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun calledFromDispatchThread() {
    mockApplication.isDispatchThread = true
    repositoryUrlManager.getLibraryRevision("com.android.support", "support-v4", null, true, fileOp)

    // When called on the dispatch thread, we return the dependency value from the local cache and post a network request on background.
    assertEquals(2, localRepo.requestCount.toLong())
    assertEquals(2, networkRepo.requestCount.toLong())
  }

  @Test
  fun calledFromWorkerThread() {
    mockApplication.isDispatchThread = false
    repositoryUrlManager.getLibraryRevision("com.android.support", "support-v4", null, true, fileOp)

    // When called on the worker thread, we return the dependency value from the network only.
    assertEquals(0, localRepo.requestCount.toLong())
    assertEquals(2, networkRepo.requestCount.toLong())
  }
}
