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
import com.android.tools.compose.debug.utils.MockClassObjectReference
import com.android.tools.compose.debug.utils.MockStringReference
import com.android.tools.compose.debug.utils.MockValueDescriptor
import com.android.tools.compose.debug.utils.invokeOnDebuggerManagerThread
import com.android.tools.compose.debug.utils.mockDebugProcess
import com.android.tools.compose.debug.utils.mockEvaluationContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer
import com.intellij.debugger.ui.tree.render.EnumerationChildrenRenderer
import com.sun.jdi.ReferenceType
import org.junit.Rule
import org.junit.Test

class KotlinMapEntryRendererTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  @Test
  fun checkRenderer() {
    // prepare
    val debugProcess: DebugProcessImpl =
      mockDebugProcess(project, projectRule.testRootDisposable) {
        val vm = this@mockDebugProcess.virtualMachineProxy.virtualMachine

        val stringType = classType("java.lang.String")

        classType("java.util.Map\$Entry") {
          method("getKey", "()Ljava/lang/Object;") {
            value(MockStringReference("key1", stringType, vm))
          }

          method("getValue", "()Ljava/lang/Object;") {
            value(MockStringReference("value1", stringType, vm))
          }
        }
      }

    val thisObjectType: ReferenceType =
      debugProcess.virtualMachineProxy.classesByName("java.util.Map\$Entry").first()

    debugProcess.invokeOnDebuggerManagerThread {
      // 1. check `Kotlin MapEntry` is the first selected renderer by default.
      val renderer =
        NodeRendererSettings.getInstance()
          .getAllRenderers(projectRule.project)
          .filter { it.isEnabled }
          .first {
            (it as? CompoundReferenceRenderer)?.isApplicableAsync(thisObjectType)?.get() == true
          }
      assertThat(renderer.name).isEqualTo("Kotlin MapEntry")

      val thisObjectValue =
        MockClassObjectReference(thisObjectType, debugProcess.virtualMachineProxy.virtualMachine)
      val evaluationContext = mockEvaluationContext(debugProcess, thisObjectValue)
      val thisValueDescriptor = MockValueDescriptor(project, thisObjectValue)

      // 2. check if the label is properly rendered - it should be "key -> value".
      val label = renderer.calcLabel(thisValueDescriptor, evaluationContext, MockitoKt.mock())
      assertThat(label).isEqualTo("key1 -> value1")

      // 3. check if `EnumerationChildrenRenderer` is the children renderer.
      val childrenRenderer =
        (renderer as CompoundReferenceRenderer).childrenRenderer as EnumerationChildrenRenderer
      assertThat(childrenRenderer.children.map { it.myName }).containsExactly("key", "value")
    }
  }
}
