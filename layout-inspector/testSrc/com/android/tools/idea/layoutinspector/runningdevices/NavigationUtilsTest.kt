/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.testutils.TestUtils
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.FakeSessionStats
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecomposeStateReadResult
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.runDispatching
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class NavigationUtilsTest {
  private val androidProjectRule = AndroidProjectRule.Companion.withSdk()
  private val fileOpenCaptureRule = FileOpenCaptureRule(androidProjectRule)

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(androidProjectRule).around(fileOpenCaptureRule)

  @get:Rule val edtRule = EdtRule()

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  @RunsInEdt
  fun testMouseDoubleClick() {
    val client = FakeInspectorClient()
    loadComposeFiles()
    val inspectorModel = createModel()
    inspectorModel.setSelection(inspectorModel[2L], SelectionOrigin.INTERNAL)

    navigateToSelectedViewFromRendererDoubleClick(
      scope = CoroutineScope(Job()),
      inspectorModel = inspectorModel,
      client = client,
      notificationModel = NotificationModel(androidProjectRule.project),
    )

    assertThat(inspectorModel.selection?.drawId).isEqualTo(2L)
    runDispatching { GotoDeclarationAction.lastAction?.join() }
    fileOpenCaptureRule.checkEditor("demo.xml", 2, "<RelativeLayout")

    assertThat(client.stats.goToSourcesFromRendererInvocations).isEqualTo(1)
  }

  private fun createModel(): InspectorModel =
    model(
      androidProjectRule.testRootDisposable,
      androidProjectRule.project,
      FakeTreeSettings(),
      body =
        DemoExample.setUpDemo(androidProjectRule.fixture) {
          view(0, qualifiedName = "androidx.ui.core.AndroidComposeView") {
            compose(-2, "Column", "MyCompose.kt", 49835523, 532, 17) {
              compose(-3, "Text", "MyCompose.kt", 49835523, 585, 18)
              compose(-4, "Greeting", "MyCompose.kt", 49835523, 614, 19) {
                compose(-5, "Text", "MyCompose.kt", 1216697758, 156, 3)
              }
            }
          }
        },
    )

  private fun loadComposeFiles() {
    val fixture = androidProjectRule.fixture
    fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/com/example/MyCompose.kt")
    fixture.copyFileToProject("java/com/example/composable/MyCompose.kt")
  }
}

private class FakeInspectorClient : InspectorClient {
  override fun registerStateCallback(callback: (InspectorClient.State) -> Unit) {}

  override fun registerErrorCallback(errorListener: InspectorClient.ErrorListener) {}

  override fun registerRootsEventCallback(callback: (List<*>) -> Unit) {}

  override fun registerTreeEventCallback(callback: (Any) -> Unit) {}

  override fun registerConnectionTimeoutCallback(
    callback: (DynamicLayoutInspectorErrorInfo.AttachErrorState) -> Unit
  ) {}

  override suspend fun connect(project: Project) {}

  override fun updateProgress(state: DynamicLayoutInspectorErrorInfo.AttachErrorState) {}

  override fun disconnect() {}

  override suspend fun startFetching() {}

  override suspend fun stopFetching() {}

  override fun refresh() {}

  override suspend fun saveSnapshot(path: Path) {}

  override val clientType = DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
  override val state = InspectorClient.State.CONNECTED
  override val stats = FakeSessionStats()
  override val process =
    object : ProcessDescriptor {
      override val device = MODERN_DEVICE
      override val abiCpuArch = "cpu"
      override val name = "name"
      override val packageName = "package_name"
      override val isRunning = true
      override val pid = 1
      override val streamId = 1L
    }
  override val treeLoader: TreeLoader
    get() = throw NotImplementedError()

  override val inLiveMode: Boolean
    get() = throw NotImplementedError()

  override val provider: PropertiesProvider
    get() = throw NotImplementedError()

  override suspend fun getRecompositionStateReadsFromCache(
    view: ComposeViewNode,
    recomposition: Int,
  ): RecomposeStateReadResult? = null
}
