package com.android.tools.idea.compose.preview.flow

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
          .trimIndent()
      )

    withTimeout(30.seconds) {
      val flowScope = createChildScope(context = workerThread)
      val flowConnected = CompletableDeferred<Unit>()
      val resourceChangedFlow =
        resourceChangedFlow(
            projectRule.module,
            projectRule.testRootDisposable,
            onConnected = { flowConnected.complete(Unit) }
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
          .trimIndent()
      )

    withTimeout(30.seconds) {
      val flowScope = createChildScope(context = workerThread)
      val flowConnected = CompletableDeferred<Unit>()
      val resourceChangedFlow =
        resourceChangedFlow(
            projectRule.module,
            projectRule.testRootDisposable,
            onConnected = { flowConnected.complete(Unit) }
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
