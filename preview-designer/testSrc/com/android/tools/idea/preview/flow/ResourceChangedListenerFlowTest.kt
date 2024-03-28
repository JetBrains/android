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
package com.android.tools.idea.preview.flow

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.res.ResourceNotificationManager.Reason
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.replaceText
import com.intellij.openapi.application.readAndWriteAction
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class ResourceChangedListenerFlowTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testVectorEditProducesFlowEvent() = runBlocking {
    val resourceFile =
      projectRule.fixture.addFileToProject(
        "res/drawable/square.xml",
        // language=XML
        """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
            <path
                android:fillColor="#FF0000"
                android:pathData="M0,0 l 100,0 0,100 -100,0 0,-100z" />
        </vector>
      """
          .trimIndent(),
      )

    withTimeout(30.seconds) {
      val flowScope = createChildScope(context = workerThread)
      val flowConnected = CompletableDeferred<Unit>()
      val resourceChangedFlow =
        resourceChangedFlow(
            projectRule.module,
            projectRule.testRootDisposable,
            onConnected = { flowConnected.complete(Unit) },
          )
          .stateIn(flowScope, SharingStarted.Eagerly, emptySet())
      flowConnected.await()

      withContext(AndroidDispatchers.uiThread) {
        projectRule.fixture.openFileInEditor(resourceFile.virtualFile)
      }
      readAndWriteAction {
        writeAction {
          projectRule.fixture.editor.executeAndSave { replaceText("#FF0000", "#00FF00") }
        }
      }
      resourceChangedFlow.awaitStatus(timeout = 5.seconds) { it == setOf(Reason.RESOURCE_EDIT) }
      flowScope.cancel()
    }
  }

  @Test
  fun testStringEditGeneratesFlow() = runBlocking {
    val resourceFile =
      projectRule.fixture.addFileToProject(
        "res/values/strings.xml",
        // language=XML
        """
        <resources>
          <string name="app_name">My Application</string>
      </resources>
      """
          .trimIndent(),
      )

    withTimeout(30.seconds) {
      val flowScope = createChildScope(context = workerThread)
      val flowConnected = CompletableDeferred<Unit>()
      val resourceChangedFlow =
        resourceChangedFlow(
            projectRule.module,
            projectRule.testRootDisposable,
            onConnected = { flowConnected.complete(Unit) },
          )
          .stateIn(flowScope, SharingStarted.Eagerly, emptySet())
      flowConnected.await()

      withContext(AndroidDispatchers.uiThread) {
        projectRule.fixture.openFileInEditor(resourceFile.virtualFile)
      }
      readAndWriteAction {
        writeAction {
          projectRule.fixture.editor.executeAndSave { replaceText("app_name", "app_name2") }
        }
      }
      resourceChangedFlow.awaitStatus(timeout = 5.seconds) { it == setOf(Reason.RESOURCE_EDIT) }
      flowScope.cancel()
    }
  }
}
