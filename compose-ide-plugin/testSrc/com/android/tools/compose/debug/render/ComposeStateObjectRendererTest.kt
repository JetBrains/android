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
package com.android.tools.compose.debug.render

import com.android.testutils.MockitoKt
import com.android.tools.compose.debug.utils.DebuggerRule
import com.android.tools.compose.debug.utils.MockClassObjectReference
import com.android.tools.compose.debug.utils.MockIntegerValue
import com.android.tools.compose.debug.utils.MockStringReference
import com.android.tools.compose.debug.utils.MockValueDescriptor
import com.android.tools.compose.debug.utils.mockDebugProcess
import com.android.tools.compose.debug.utils.mockEvaluationContext
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer
import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit

class ComposeStateObjectRendererTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val debuggerRule = DebuggerRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(debuggerRule)

  private val project
    get() = projectRule.project

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_STATE_OBJECT_CUSTOM_RENDERER.override(true, projectRule.fixture.testRootDisposable)
  }

  @Test
  fun renderList() {
    val source = """
      package androidx.compose.runtime.snapshots

      class SnapshotStateList<T> {}
    """.trimIndent()
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/snapshots/SnapshotStateList.kt", source)

    // prepare
    val debugProcess: DebugProcessImpl = mockDebugProcess(project) {
      val vm = this@mockDebugProcess.virtualMachineProxy.virtualMachine

      val listType = classType("java.util.List") {
        method("size", "()I") {
          value(MockIntegerValue(7, vm))
        }
      }

      classType("androidx.compose.runtime.snapshots.SnapshotStateList") {
        method("getDebuggerDisplayValue", "()Ljava/util/List;") {
          value(MockClassObjectReference(listType, vm))
        }
      }
    }

    val thisObjectType: ReferenceType = debugProcess.virtualMachineProxy
      .classesByName("androidx.compose.runtime.snapshots.SnapshotStateList")
      .first()

    debuggerRule.invokeOnDebuggerManagerThread {
      // 1. check `Compose SnapshotStateList` is the first selected renderer by default.
      val renderer = NodeRendererSettings.getInstance().getAllRenderers(projectRule.project)
        .filter { it.isEnabled }
        .first { (it as? CompoundReferenceRenderer)?.isApplicableAsync(thisObjectType)?.get() == true }
      assertThat(renderer.name).isEqualTo("Compose State Object")

      val thisObjectValue = MockClassObjectReference(thisObjectType, debugProcess.virtualMachineProxy.virtualMachine)
      val evaluationContext = mockEvaluationContext(debugProcess, thisObjectValue)
      val thisValueDescriptor = MockValueDescriptor(project, thisObjectValue)

      // 2. check if the label is eventually properly rendered - it should be size = xx.
      renderer.calcLabel(thisValueDescriptor, evaluationContext, MockitoKt.mock())
      waitForCondition(500, TimeUnit.MILLISECONDS) {
        thisValueDescriptor.valueText == " size = 7"
      }

      // 3. check if the children renderer is the same as the label renderer.
      val childrenRenderer = (renderer as CompoundReferenceRenderer).childrenRenderer
      assertThat(childrenRenderer.uniqueId).isEqualTo("androidx.compose.runtime.snapshots.SnapshotStateList")
    }
  }

  @Test
  fun renderMap() {
    val source = """
      package androidx.compose.runtime.snapshots

      class SnapshotStateMap<K, V> {}
    """.trimIndent()
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/snapshots/SnapshotStateMap.kt", source)

    // prepare
    val debugProcess: DebugProcessImpl = mockDebugProcess(project) {
      val vm = this@mockDebugProcess.virtualMachineProxy.virtualMachine

      val mapType = classType("java.util.Map") {
        method("size", "()I") {
          value(MockIntegerValue(5, vm))
        }
      }

      classType("androidx.compose.runtime.snapshots.SnapshotStateMap") {
        method("getDebuggerDisplayValue") {
          value(MockClassObjectReference(mapType, vm))
        }
      }
    }

    val thisObjectType: ReferenceType = debugProcess.virtualMachineProxy
      .classesByName("androidx.compose.runtime.snapshots.SnapshotStateMap")
      .first()

    debuggerRule.invokeOnDebuggerManagerThread {
      // 1. check `"Compose SnapshotStateList"` is the first selected renderer by default.
      val renderer = NodeRendererSettings.getInstance().getAllRenderers(projectRule.project)
        .filter { it.isEnabled }
        .first { (it as? CompoundReferenceRenderer)?.isApplicableAsync(thisObjectType)?.get() == true }
      assertThat(renderer.name).isEqualTo("Compose State Object")

      val thisObjectValue = MockClassObjectReference(thisObjectType, debugProcess.virtualMachineProxy.virtualMachine)
      val evaluationContext = mockEvaluationContext(debugProcess, thisObjectValue)
      val thisValueDescriptor = MockValueDescriptor(project, thisObjectValue)

      // 2. check if the label is eventually properly rendered - it should be size = xx.
      renderer.calcLabel(thisValueDescriptor, evaluationContext, MockitoKt.mock())
      waitForCondition(500, TimeUnit.MILLISECONDS) {
        thisValueDescriptor.valueText == " size = 5"
      }

      // 3. check if the children renderer is the same as the label renderer.
      val childrenRenderer = (renderer as CompoundReferenceRenderer).childrenRenderer
      assertThat(childrenRenderer.uniqueId).isEqualTo("androidx.compose.runtime.snapshots.SnapshotStateMap")
    }
  }

  @Test
  fun renderInteger() {
    val source = """
      package androidx.compose.runtime

      open class SnapshotMutableStateImpl<T> {}

      class ParcelableSnapshotMutableState : SnapshotMutableStateImpl<T>
    """.trimIndent()
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/SnapshotMutableStateImpl.kt", source)

    val debugProcess: DebugProcessImpl = mockDebugProcess(project) {
      val vm = this@mockDebugProcess.virtualMachineProxy.virtualMachine

      val snapshotMutableStateImplType = classType("androidx.compose.runtime.SnapshotMutableStateImpl") {
        method("getDebuggerDisplayValue") {
          value(MockIntegerValue(1, vm))
        }
      }

      classType("androidx.compose.runtime.ParcelableSnapshotMutableState", snapshotMutableStateImplType as ClassType) {
        method("getDebuggerDisplayValue") {
          value(MockIntegerValue(2, vm))
        }
      }
    }

    val thisObjectType: ReferenceType = debugProcess.virtualMachineProxy
      .classesByName("androidx.compose.runtime.ParcelableSnapshotMutableState")
      .first()

    debuggerRule.invokeOnDebuggerManagerThread {
      // check `Compose SnapshotState` is the first selected renderer by default.
      val renderer = NodeRendererSettings.getInstance().getAllRenderers(project)
        .filter { it.isEnabled }
        .first { (it as? CompoundReferenceRenderer)?.isApplicableAsync(thisObjectType)?.get() == true }

      assertThat(renderer.name).isEqualTo("Compose State Object")

      val thisObjectValue = MockClassObjectReference(thisObjectType, debugProcess.virtualMachineProxy.virtualMachine)
      val thisValueDescriptor = MockValueDescriptor(project, thisObjectValue)
      val evaluationContext = mockEvaluationContext(debugProcess, thisObjectValue)

      // check if the label is eventually properly rendered - it should be the label calculated for the underlying value.
      renderer.calcLabel(thisValueDescriptor, evaluationContext, MockitoKt.mock())
      waitForCondition(500, TimeUnit.MILLISECONDS) {
        thisValueDescriptor.valueText == "2"
      }

      // check if the children renderer is the same as the label renderer.
      val childrenRenderer = (renderer as CompoundReferenceRenderer).childrenRenderer
      assertThat(childrenRenderer.uniqueId).isEqualTo("androidx.compose.runtime.SnapshotMutableStateImpl")
    }
  }

  @Test
  fun renderString() {
    val source = """
      package androidx.compose.runtime

      private class DerivedSnapshotState<T> {}
    """.trimIndent()
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/DerivedSnapshotState.kt", source)

    val debugProcess: DebugProcessImpl = mockDebugProcess(project) {
      val vm = this@mockDebugProcess.virtualMachineProxy.virtualMachine

      val stringType = classType("java.lang.String")

      classType("androidx.compose.runtime.DerivedSnapshotState") {
        method("getDebuggerDisplayValue") {
          value(MockStringReference("This is fake string value.", stringType, vm))
        }
      }
    }

    val thisObjectType: ReferenceType = debugProcess.virtualMachineProxy
      .classesByName("androidx.compose.runtime.DerivedSnapshotState")
      .first()

    debuggerRule.invokeOnDebuggerManagerThread {
      // check `Compose SnapshotState` is the first selected renderer by default.
      val renderer = NodeRendererSettings.getInstance().getAllRenderers(projectRule.project)
        .filter { it.isEnabled }
        .first { (it as? CompoundReferenceRenderer)?.isApplicableAsync(thisObjectType)?.get() == true }
      assertThat(renderer.name).isEqualTo("Compose State Object")

      val thisObjectValue = MockClassObjectReference(thisObjectType, debugProcess.virtualMachineProxy.virtualMachine)
      val evaluationContext = mockEvaluationContext(debugProcess, thisObjectValue)
      val thisValueDescriptor = MockValueDescriptor(project, thisObjectValue)

      // check if the label is eventually properly rendered - it should be the label calculated for the underlying value.
      renderer.calcLabel(thisValueDescriptor, evaluationContext, MockitoKt.mock())
      waitForCondition(500, TimeUnit.MILLISECONDS) {
        thisValueDescriptor.valueText == "This is fake string value."
      }

      // check if the children renderer is the same as the label renderer.
      val childrenRenderer = (renderer as CompoundReferenceRenderer).childrenRenderer
      assertThat(childrenRenderer.uniqueId).isEqualTo("androidx.compose.runtime.DerivedSnapshotState")
    }
  }

  @Test
  fun checkApplicable_NoValidMethod() {
    val source = """
      package androidx.compose.runtime.snapshots

      class SnapshotStateList<T> {}
    """.trimIndent()
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/snapshots/SnapshotStateList.kt", source)

    // prepare
    val debugProcess: DebugProcessImpl = mockDebugProcess(project) {
      val vm = this@mockDebugProcess.virtualMachineProxy.virtualMachine
      classType("java.lang.Object") {
        method("toString", "()Ljava/lang/String;")
      }

      val stringType = classType("java.lang.String")

      classType("androidx.compose.runtime.snapshots.SnapshotStateList") {
        // Please note: method `getDebuggerDisplayValue` is not declared.

        method("toString", "()Ljava/lang/String;") {
          value(MockStringReference("SnapshotStateList@1234", stringType, vm))
        }
      }
    }

    val thisObjectType: ReferenceType = debugProcess.virtualMachineProxy
      .classesByName("androidx.compose.runtime.snapshots.SnapshotStateList")
      .first()

    debuggerRule.invokeOnDebuggerManagerThread {
      // 1. Check if `Compose State Object` is the first selected renderer by default since
      // `getDebuggerDisplayValue` method is not found.
      val renderer = NodeRendererSettings.getInstance().getAllRenderers(projectRule.project)
        .filter { it.isEnabled }
        .first { (it as? CompoundReferenceRenderer)?.isApplicableAsync(thisObjectType)?.get() == true }
      assertThat(renderer.name).isEqualTo("Compose State Object")

      val thisObjectValue = MockClassObjectReference(thisObjectType, debugProcess.virtualMachineProxy.virtualMachine)
      val evaluationContext = mockEvaluationContext(debugProcess, thisObjectValue)
      val thisValueDescriptor = MockValueDescriptor(project, thisObjectValue)

      // 2. check if the label is eventually properly rendered - no errors like
      // `Unable to evaluate the expression No such instance method: 'getDebuggerDisplayValue'`.
      renderer.calcLabel(thisValueDescriptor, evaluationContext, MockitoKt.mock())
      waitForCondition(500, TimeUnit.MILLISECONDS) {
        thisValueDescriptor.valueText == "SnapshotStateList@1234"
      }
    }
  }
}