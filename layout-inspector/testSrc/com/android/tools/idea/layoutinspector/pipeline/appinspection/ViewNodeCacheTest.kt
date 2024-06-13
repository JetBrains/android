/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.ROOT2
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors

class ViewNodeCacheTest {
  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val cleaner = MockitoCleanerRule()

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @Before
  fun init() {
    val executors: AndroidExecutors = mock()
    whenever(executors.workerThreadExecutor).thenReturn(Executors.newSingleThreadExecutor())
    ApplicationManager.getApplication()
      .registerServiceInstance(AndroidExecutors::class.java, executors, disposableRule.disposable)
  }

  @Test
  fun testThreading() {
    val model =
      model(disposableRule.disposable) {
        view(ROOT, x = 2, y = 4, width = 6, height = 8, qualifiedName = "root") {
          view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
            view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
          }
          view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
        }
      }
    assertThat(model[VIEW1]).isNotNull()
    val cache =
      object : ViewNodeCache<Int>(model) {
        override suspend fun fetchDataFor(root: ViewNode, node: ViewNode): Int = 7
      }
    cache.allowFetching = true
    var stop = false
    var modelLoops = 0
    var lookupLoops = 0

    val modelRunnable: () -> Unit = {
      assertThat(model[VIEW1]).isNotNull()
      while (!stop) {
        update(model, cache, window2(), listOf(ROOT, ROOT2))
        update(model, cache, window2(), listOf(ROOT2))
        update(model, cache, window1(), listOf(ROOT, ROOT2))
        update(model, cache, window1(), listOf(ROOT))
        modelLoops++
      }
    }

    val lookupRunnable: () -> Unit = {
      runBlocking {
        while (!stop) {
          model[VIEW1]?.let { cache.getDataFor(it) }
          model[VIEW4]?.let { cache.getDataFor(it) }
          lookupLoops++
        }
      }
    }

    var exception: Throwable? = null
    val handler = Thread.UncaughtExceptionHandler { _, ex -> exception = ex }
    val t1 = Thread(modelRunnable)
    val t2 = Thread(lookupRunnable)
    t1.uncaughtExceptionHandler = handler
    t2.uncaughtExceptionHandler = handler
    t1.start()
    t2.start()
    while (t1.isAlive && modelLoops < 1000 || t2.isAlive && lookupLoops < 1000) {
      Thread.sleep(10L)
    }
    stop = true
    t1.join(1000L)
    t2.join(1000L)
    exception?.let { throw it }
  }

  private fun window1() =
    window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
      view(VIEW1, 8, 6, 4, 2, qualifiedName = "v1Type") {
        view(VIEW3, 9, 8, 7, 6, qualifiedName = "v3Type")
      }
      view(VIEW2, 6, 7, 8, 9, qualifiedName = "v2Type")
    }

  private fun window2() =
    window(ROOT2, ROOT2, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
      view(VIEW4, 8, 6, 4, 2, qualifiedName = "v4Type")
    }

  private fun update(
    model: InspectorModel,
    cache: ViewNodeCache<*>,
    window: AndroidWindow,
    allIds: List<Long>,
  ) {
    model.update(window, allIds, 0)
    cache.retain(allIds)
  }
}
