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
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecomposeStateReadResult
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.convertStateRead
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.RecompositionStateReadResponse
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import fleet.util.async.firstNotNull
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetRecompositionStateReadResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.Parameter.Type
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val ANCHOR1 = 101
private const val ANCHOR2 = 102

class StateInspectionModelTest {
  private val disposableRule = DisposableRule()
  private val projectRule = ProjectRule()
  @get:Rule val chain = RuleChain(projectRule, disposableRule)

  private lateinit var disposable: Disposable
  private lateinit var inspectorModel: InspectorModel
  private lateinit var compose1: ComposeViewNode
  private lateinit var compose2: ComposeViewNode
  private val treeSettings = FakeTreeSettings()
  private val read2Anchor1 = RecompositionStateReadResponse {
    AnchorHash(ANCHOR1)
    RecompositionStateRead {
      Recomposition(2)
      StateRead {
        Parameter("value", Type.DIMENSION_DP, 1.0f)
        Invalidated(true)
        StackTraceLine("androidx.CompositionImpl", "recordReadOf", "Composition.kt", 1015)
        StackTraceLine("androidx.SnapshotKt", "readable", "Snapshot.kt", 225)
      }
    }
  }
  private val read3Anchor1 = RecompositionStateReadResponse {
    AnchorHash(ANCHOR1)
    RecompositionStateRead {
      Recomposition(3)
      StateRead {
        Parameter("value", Type.DIMENSION_DP, 1.5f)
        StackTraceLine("androidx.CompositionImpl", "recordReadOf", "Composition.kt", 1015)
        StackTraceLine("androidx.SnapshotKt", "readable", "Snapshot.kt", 225)
      }
    }
  }

  @Before
  fun before() {
    disposable = disposableRule.disposable
    inspectorModel =
      model(disposable, projectRule.project) {
        view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
          view(VIEW1, 0, 0, 100, 200) {
            compose(
              COMPOSE1,
              "Item",
              anchorHash = ANCHOR1,
              composeCount = 1,
              composeFilename = "MainActivity.kt",
            )
            compose(
              COMPOSE2,
              "Text",
              anchorHash = ANCHOR2,
              composeCount = 2,
              composeFilename = "MainActivity.kt",
            )
          }
        }
      }
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    compose1 = inspectorModel[COMPOSE1] as ComposeViewNode
    compose2 = inspectorModel[COMPOSE2] as ComposeViewNode
  }

  @Test
  fun testDefaults() = runTest {
    val client = FakeClient(projectRule.project, this, disposable)
    val model = StateInspectionModelImpl(inspectorModel, this, treeSettings, { client }, disposable)
    assertThat(model.show.value).isFalse()
    assertThat(model.recompositionText.value).isEqualTo("")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
  }

  @Test
  fun testNodeSelectedInForAllMode() = runTest {
    treeSettings.observeStateReadsForAll = true
    val client = FakeClient(projectRule.project, this, disposable)
    val model = StateInspectionModelImpl(inspectorModel, this, treeSettings, { client }, disposable)
    inspectorModel.stateReadsNode = compose1
    testScheduler.advanceUntilIdle()

    // Only show should have changed before result is received:
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(1)
    assertThat(model.recompositionText.value).isEqualTo("")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()
    assertThat(client.requestedNode).isEqualTo(compose1)
    assertThat(client.requestedRecomposition).isEqualTo(1)

    // The result is received from the agent:
    client.emitResult(read2Anchor1.convert(compose1, 1))
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(2)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.stateReadsText.value).isEqualTo("State Reads: 1")
    assertThat(model.stackTraceText.value)
      .isEqualTo(
        """
        State read value: 1.0dp 🟢
            at androidx.CompositionImpl.recordReadOf(Composition.kt:1015)
            at androidx.SnapshotKt.readable(Snapshot.kt:225)


      """
          .trimIndent()
      )
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()
  }

  @Test
  fun testNodeSelectedInOnDemandMode() = runTest {
    treeSettings.observeStateReadsForAll = false
    val client = FakeClient(projectRule.project, this, disposable)
    val model = StateInspectionModelImpl(inspectorModel, this, treeSettings, { client }, disposable)
    assertThat(model.updates.value).isEqualTo(1)

    // Select compose1 for showing state reads:
    inspectorModel.stateReadsNode = compose1
    testScheduler.advanceUntilIdle()
    assertThat(client.requestedNode).isEqualTo(compose1)
    assertThat(client.requestedRecomposition).isEqualTo(1)

    // The client should request state reads from the agent, but nothing will happen before
    // the application changes and the recomposition count for compose1 increases.
    assertThat(model.show.value).isTrue() // We will now show the state read window...
    assertThat(model.updates.value).isEqualTo(2)
    assertThat(model.recompositionText.value).isEqualTo("Waiting for interactions")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()

    // Select compose1 again for showing state reads:
    inspectorModel.stateReadsNode = compose1
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(3)

    // The extra request may result in an empty result.
    // The empty result will be ignored...
    client.emitResult(null)
    testScheduler.advanceUntilIdle()
    assertThat(model.recompositionText.value).isEqualTo("Waiting for interactions")

    // Emulate an update that adds a recomposition for compose1.
    // The prev/next actions will remain disabled.
    val updatedRecompositionCounts =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 0, 0, 100, 200) {
          compose(
            COMPOSE1,
            "Item",
            anchorHash = ANCHOR1,
            composeCount = 2,
            composeFilename = "MainActivity.kt",
          )
          compose(
            COMPOSE2,
            "Text",
            anchorHash = ANCHOR2,
            composeCount = 2,
            composeFilename = "MainActivity.kt",
          )
        }
      }
    inspectorModel.update(updatedRecompositionCounts, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(4)
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()

    // The result is received from the agent:
    client.emitResult(read2Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(5)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.stateReadsText.value).isEqualTo("State Reads: 1")
    assertThat(model.stackTraceText.value)
      .isEqualTo(
        """
        State read value: 1.0dp 🟢
            at androidx.CompositionImpl.recordReadOf(Composition.kt:1015)
            at androidx.SnapshotKt.readable(Snapshot.kt:225)


      """
          .trimIndent()
      )
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()
  }

  @Test
  fun testPrevAndNext() = runTest {
    treeSettings.observeStateReadsForAll = false
    val client = FakeClient(projectRule.project, this, disposable)
    val model = StateInspectionModelImpl(inspectorModel, this, treeSettings, { client }, disposable)
    assertThat(model.updates.value).isEqualTo(1)

    // Select compose1 for showing state reads:
    inspectorModel.stateReadsNode = compose1
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(2)

    // The client should request state reads from the agent, but nothing will happen before
    // the application changes and the recomposition count for compose1 increases.
    // Emulate an update that adds 2 recompositions for compose1.
    // The prev/next actions will remain disabled.
    val updatedRecompositionCounts =
      window(ROOT, ROOT, 2, 4, 6, 8, rootViewQualifiedName = "rootType") {
        view(VIEW1, 0, 0, 100, 200) {
          compose(
            COMPOSE1,
            "Item",
            anchorHash = ANCHOR1,
            composeCount = 3,
            composeFilename = "MainActivity.kt",
          )
          compose(
            COMPOSE2,
            "Text",
            anchorHash = ANCHOR2,
            composeCount = 2,
            composeFilename = "MainActivity.kt",
          )
        }
      }
    inspectorModel.update(updatedRecompositionCounts, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(3)
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()

    // A state read is finally received from the compose agent:
    client.emitResult(read2Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(4)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isTrue()

    model.nextAction.perform()
    client.emitResult(read3Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(5)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 3")
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isFalse()

    model.prevAction.perform()
    client.emitResult(read2Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(6)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isTrue()
  }

  @Test
  fun testMinimizeForAll() = runTest {
    treeSettings.observeStateReadsForAll = true
    val client = FakeClient(projectRule.project, this, disposable)
    val model = StateInspectionModelImpl(inspectorModel, this, treeSettings, { client }, disposable)
    assertThat(model.show.value).isFalse()

    inspectorModel.stateReadsNode = compose1
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()
    assertThat(client.requestedNode).isEqualTo(compose1)
    assertThat(client.requestedRecomposition).isEqualTo(1)

    // The result is received from the agent:
    client.emitResult(read2Anchor1.convert(compose1, 1))
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()

    model.minimizeAction.perform()
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isFalse()
  }

  @Test
  fun testMinimizeOnDemand() = runTest {
    treeSettings.observeStateReadsForAll = false
    val client = FakeClient(projectRule.project, this, disposable)
    val model = StateInspectionModelImpl(inspectorModel, this, treeSettings, { client }, disposable)
    assertThat(model.show.value).isFalse()

    inspectorModel.stateReadsNode = compose1
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()
    assertThat(client.requestedNode).isEqualTo(compose1)
    assertThat(client.requestedRecomposition).isEqualTo(1)

    // The result is received from the agent:
    client.emitResult(read2Anchor1.convert(compose1, 1))
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()

    model.minimizeAction.perform()
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isFalse()
    assertThat(client.requestedNode).isEqualTo(compose1)
    assertThat(client.requestedRecomposition).isEqualTo(0)
    client.emitResult(null)
  }

  private fun AnAction.isEnabled(): Boolean {
    val presentation = templatePresentation.clone()
    val event =
      AnActionEvent.createEvent(
        DataContext.EMPTY_CONTEXT,
        presentation,
        ActionPlaces.UNKNOWN,
        ActionUiKind.NONE,
        null,
      )
    ActionUtil.performDumbAwareUpdate(this, event, false)
    return presentation.isEnabled
  }

  private fun AnAction.perform() {
    val presentation = templatePresentation.clone()
    val event =
      AnActionEvent.createEvent(
        DataContext.EMPTY_CONTEXT,
        presentation,
        ActionPlaces.UNKNOWN,
        ActionUiKind.NONE,
        null,
      )
    ActionUtil.performDumbAwareUpdate(this, event, true)
    ActionUtil.performActionDumbAwareWithCallbacks(this, event)
  }

  private fun GetRecompositionStateReadResponse.convert(
    node: ComposeViewNode,
    firstObservedRecomposition: Int = 1,
  ): RecomposeStateReadResult {
    val reads = convertStateRead(this, inspectorModel)
    return RecomposeStateReadResult(
      node,
      read.recompositionNumber,
      reads,
      firstObservedRecomposition,
    )
  }

  private class FakeClient(project: Project, scope: CoroutineScope, disposable: Disposable) :
    FakeInspectorClient(project, MODERN_DEVICE.createProcess(), scope, disposable) {
    private val holder = MutableStateFlow<ResultHolder?>(null)
    var requestedNode: ComposeViewNode? = null
      private set

    var requestedRecomposition: Int = -1
      private set

    fun emitResult(result: RecomposeStateReadResult?) {
      holder.value = ResultHolder(result)
    }

    override suspend fun getRecompositionStateReadsFromCache(
      view: ComposeViewNode,
      recomposition: Int,
    ): RecomposeStateReadResult? {
      requestedNode = view
      requestedRecomposition = recomposition

      val result = holder.firstNotNull().result
      holder.value = null
      return result
    }

    private class ResultHolder(val result: RecomposeStateReadResult?)
  }

  // TODO(b/441724255) Move this class and other similar fake clients to testingSrc
  private open class FakeInspectorClient(
    project: Project,
    process: ProcessDescriptor,
    scope: CoroutineScope,
    parentDisposable: Disposable,
  ) :
    AbstractInspectorClient(
      DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT,
      project,
      NotificationModel(project),
      process,
      DisconnectedClient.stats,
      scope,
      parentDisposable,
    ) {
    override suspend fun startFetching() = throw NotImplementedError()

    override suspend fun stopFetching() = throw NotImplementedError()

    override fun refresh() = throw NotImplementedError()

    override suspend fun saveSnapshot(path: Path) = throw NotImplementedError()

    override suspend fun doConnect() {}

    override suspend fun doDisconnect() {}

    override val capabilities
      get() = throw NotImplementedError()

    override val treeLoader: TreeLoader
      get() = throw NotImplementedError()

    override val inLiveMode: Boolean
      get() = false

    override val provider: PropertiesProvider
      get() = throw NotImplementedError()

    override suspend fun getRecompositionStateReadsFromCache(
      view: ComposeViewNode,
      recomposition: Int,
    ): RecomposeStateReadResult? = null
  }
}
