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

import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecomposeStateReadResult
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.convertStateRead
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.RecompositionStateReadResponse
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.test.TestScope
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

  private lateinit var inspectorModel: InspectorModel
  private lateinit var view1: ViewNode
  private lateinit var compose1: ComposeViewNode
  private lateinit var compose2: ComposeViewNode
  private val read1Anchor1 = RecompositionStateReadResponse {
    AnchorHash(ANCHOR1)
    StateReadGroup {
      Recomposition(1)
      StateRead {
        Parameter("value", Type.DIMENSION_DP, 0.5f)
        Invalidated(true)
        StackTraceLine("androidx.CompositionImpl", "recordReadOf", "Composition.kt", 1015)
        StackTraceLine("androidx.SnapshotKt", "readable", "Snapshot.kt", 225)
      }
    }
  }
  private val read2Anchor1 = RecompositionStateReadResponse {
    AnchorHash(ANCHOR1)
    StateReadGroup {
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
    StateReadGroup {
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
    inspectorModel =
      model(disposableRule.disposable, projectRule.project) {
        view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
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
              composeCount = 3,
              composeFilename = "MainActivity.kt",
            )
          }
        }
      }
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    view1 = inspectorModel[VIEW1] as ViewNode
    compose1 = inspectorModel[COMPOSE1] as ComposeViewNode
    compose2 = inspectorModel[COMPOSE2] as ComposeViewNode
  }

  @Test
  fun testDefaults() = runTestWithDisposable { disposable ->
    var results = 0
    val model = StateInspectionModelImpl(inspectorModel, this, disposable) { results++ }
    assertThat(model.show.value).isFalse()
    assertThat(model.recompositionText.value).isEqualTo("")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
    assertThat(model.composableInspected.value).isEqualTo(null)
    assertThat(results).isEqualTo(0)
  }

  @Test
  fun testNodeSelection() = runTestWithDisposable { disposable ->
    var results = 0
    val model = StateInspectionModelImpl(inspectorModel, this, disposable) { results++ }
    inspectorModel.setSelection(compose1, SelectionOrigin.INTERNAL)
    inspectorModel.stateReadsModel.observeNode(compose1)
    inspectorModel.stateReadsModel.requestStateReadFor(compose1)
    testScheduler.advanceUntilIdle()

    // Nothing to show yet:
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(3)
    assertThat(model.recompositionText.value).isEqualTo("Waiting for interactions")
    assertThat(model.emptyStateText.value)
      .isEqualTo(
        "The selected composable has not recomposed yet.\n" +
          "Try interacting with the app to cause recompositions."
      )
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
    assertThat(model.composableInspected.value).isEqualTo(null)
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()
    assertThat(results).isEqualTo(0)

    // The result is received from the agent:
    inspectorModel.stateReadsModel.stateReads.emit(read2Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(4)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.emptyStateText.value).isEmpty()
    assertThat(model.stateReadsText.value).isEqualTo("State Reads: 1")
    assertThat(model.stackTraceText.value)
      .isEqualTo(
        """
        State read value: 1.0dp <invalidated>
            at androidx.CompositionImpl.recordReadOf(Composition.kt:1015)
            at androidx.SnapshotKt.readable(Snapshot.kt:225)


      """
          .trimIndent()
      )
    assertThat(model.composableInspected.value)
      .isEqualTo(ComposableDefinition("Item", "MainActivity.kt"))
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()
    assertThat(results).isEqualTo(1)

    // Selecting the same node should not cause updates:
    inspectorModel.setSelection(compose1, SelectionOrigin.INTERNAL)
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(4)
    assertThat(results).isEqualTo(1)

    // Selecting a ViewNode:
    inspectorModel.setSelection(view1, SelectionOrigin.INTERNAL)
    testScheduler.advanceUntilIdle()

    // There are no state reads for View nodes:
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(5)
    assertThat(model.recompositionText.value).isEqualTo("Not a compose node")
    assertThat(model.emptyStateText.value)
      .isEqualTo(
        "The selected node is a View. State reads are not supported for views.\n" +
          "Select a compose node to see recomposition state reads."
      )
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
    assertThat(model.composableInspected.value).isEqualTo(null)
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()

    // Selecting an observed composable:
    inspectorModel.setSelection(compose1, SelectionOrigin.INTERNAL)
    testScheduler.advanceUntilIdle()

    // Now the previously shown state reads are shown again:
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(6)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.stateReadsText.value).isEqualTo("State Reads: 1")
    assertThat(model.stackTraceText.value)
      .isEqualTo(
        """
        State read value: 1.0dp <invalidated>
            at androidx.CompositionImpl.recordReadOf(Composition.kt:1015)
            at androidx.SnapshotKt.readable(Snapshot.kt:225)


      """
          .trimIndent()
      )
    assertThat(model.composableInspected.value)
      .isEqualTo(ComposableDefinition("Item", "MainActivity.kt"))
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()
    assertThat(results).isEqualTo(2)

    // Selecting a non observed composable:
    inspectorModel.setSelection(compose2, SelectionOrigin.INTERNAL)
    testScheduler.advanceUntilIdle()

    // There are no state reads for a non observed node:
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(7)
    assertThat(model.recompositionText.value).isEqualTo("Node is not observed")
    assertThat(model.emptyStateText.value)
      .isEqualTo(
        "The selected composable is not being observed.\n" +
          "Select a different node to see recomposition state reads."
      )
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
    assertThat(model.composableInspected.value).isEqualTo(null)
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()

    // Make it observable:
    inspectorModel.stateReadsModel.observeNode(compose2)
    testScheduler.advanceUntilIdle()

    // We are now waiting for state reads for compose2:
    assertThat(model.show.value).isTrue()
    assertThat(model.updates.value).isEqualTo(8)
    assertThat(model.recompositionText.value).isEqualTo("Waiting for interactions")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")
    assertThat(model.composableInspected.value).isEqualTo(null)
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(model.minimizeAction.isEnabled()).isTrue()
  }

  @Test
  fun testPrevAndNext() = runTestWithDisposable { disposable ->
    var results = 0
    val model = StateInspectionModelImpl(inspectorModel, this, disposable) { results++ }
    assertThat(model.updates.value).isEqualTo(1)

    // Select compose1 for showing state reads:
    inspectorModel.stateReadsModel.requestStateReadFor(compose1)
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(3)

    // A state read is received from the compose agent:
    inspectorModel.stateReadsModel.stateReads.emit(read2Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(4)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(results).isEqualTo(1)

    // Emulate an update that adds 1 recomposition for compose1.
    // Expect the next action to become enabled.
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
            composeCount = 3,
            composeFilename = "MainActivity.kt",
          )
        }
      }
    inspectorModel.update(updatedRecompositionCounts, listOf(ROOT), 0)
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(5)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isTrue()

    model.nextAction.perform()
    testScheduler.advanceUntilIdle()
    assertThat(inspectorModel.stateReadsModel.stateReadRequested.value)
      .isEqualTo(StateReadKey(compose1, 3))
    assertThat(model.updates.value).isEqualTo(6)
    assertThat(model.recompositionText.value).isEqualTo("Waiting for interactions")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")

    inspectorModel.stateReadsModel.stateReads.emit(read3Anchor1.convert(compose1, 3))
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(7)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 3")
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isFalse()
    assertThat(results).isEqualTo(2)

    model.prevAction.perform()
    testScheduler.advanceUntilIdle()
    assertThat(inspectorModel.stateReadsModel.stateReadRequested.value)
      .isEqualTo(StateReadKey(compose1, 2))
    assertThat(model.updates.value).isEqualTo(8)
    assertThat(model.recompositionText.value).isEqualTo("Waiting for interactions")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")

    inspectorModel.stateReadsModel.stateReads.emit(read2Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(9)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 2")
    assertThat(model.prevAction.isEnabled()).isTrue()
    assertThat(model.nextAction.isEnabled()).isTrue()
    assertThat(results).isEqualTo(3)

    model.prevAction.perform()
    testScheduler.advanceUntilIdle()
    assertThat(inspectorModel.stateReadsModel.stateReadRequested.value)
      .isEqualTo(StateReadKey(compose1, 1))
    assertThat(model.updates.value).isEqualTo(10)
    assertThat(model.recompositionText.value).isEqualTo("Waiting for interactions")
    assertThat(model.stateReadsText.value).isEqualTo("")
    assertThat(model.stackTraceText.value).isEqualTo("")

    inspectorModel.stateReadsModel.stateReads.emit(
      read1Anchor1.convert(compose1, 1, hasPrevious = false)
    )
    testScheduler.advanceUntilIdle()
    assertThat(model.updates.value).isEqualTo(11)
    assertThat(model.recompositionText.value).isEqualTo("Recomposition 1")
    assertThat(model.prevAction.isEnabled()).isFalse()
    assertThat(model.nextAction.isEnabled()).isTrue()
    assertThat(results).isEqualTo(4)
  }

  @Test
  fun testMinimize() = runTestWithDisposable { disposable ->
    var results = 0
    val model = StateInspectionModelImpl(inspectorModel, this, disposable) { results++ }
    assertThat(model.show.value).isFalse()

    inspectorModel.stateReadsModel.requestStateReadFor(compose1)
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()
    assertThat(results).isEqualTo(0)

    // The result is received from the agent:
    inspectorModel.stateReadsModel.stateReads.emit(read2Anchor1.convert(compose1, 2))
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isTrue()
    assertThat(results).isEqualTo(1)

    model.minimizeAction.perform()
    testScheduler.advanceUntilIdle()
    assertThat(model.show.value).isFalse()
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
    ActionUtil.updateAction(this, event)
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
    runInEdtAndWait {
      ActionUtil.updateAction(this, event)
      ActionUtil.performAction(this, event)
    }
  }

  private fun GetRecompositionStateReadResponse.convert(
    node: ComposeViewNode,
    recomposition: Int,
    hasPrevious: Boolean = true,
  ): RecomposeStateReadResult {
    val reads = convertStateRead(this, inspectorModel)
    val data = reads[recomposition] ?: error("recomposition: $recomposition not found")
    return RecomposeStateReadResult(StateReadKey(node, recomposition), data, hasPrevious)
  }

  private fun runTestWithDisposable(testBody: suspend TestScope.(disposable: Disposable) -> Unit) {
    val disposable = Disposer.newDisposable()
    Disposer.register(disposableRule.disposable, disposable)
    runTest {
      testBody(disposable)
      Disposer.dispose(disposable)
    }
  }
}
