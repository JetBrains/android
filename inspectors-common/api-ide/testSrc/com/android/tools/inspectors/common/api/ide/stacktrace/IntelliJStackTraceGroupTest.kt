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
package com.android.tools.inspectors.common.api.ide.stacktrace

import com.android.testutils.delayUntilCondition
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class IntelliJStackTraceGroupTest {
  @get:Rule val projectRule: ProjectRule = ProjectRule()

  @get:Rule val disposableRule: DisposableRule = DisposableRule()

  @Test
  fun canOnlySelectOneStackTraceViewAtATime() =
    runBlocking<Unit> {
      val group =
        IntelliJStackTraceGroup(projectRule.project) { project: Project?, model: StackTraceModel? ->
          IntelliJStackTraceViewTest.createStackTraceView(project, model, disposableRule.disposable)
        }

      val model1 = IntelliJStackTraceViewTest.createStackTraceModel()
      val model2 = IntelliJStackTraceViewTest.createStackTraceModel()

      // IntelliJStackTraceGroup always creates IntelliJStackTraceView instances
      val view1 = group.createStackView(model1) as IntelliJStackTraceView
      val view2 = group.createStackView(model2) as IntelliJStackTraceView

      model1.setStackFrames(STACK_STRING_A)
      model2.setStackFrames(STACK_STRING_B)

      delayUntilCondition(200) { view1.listView.model.size > 0 && view2.listView.model.size > 0 }
      assertThat(view1.listView.selectedIndex).isLessThan(0)
      assertThat(view2.listView.selectedIndex).isLessThan(0)

      view1.listView.selectedIndex = 1

      assertThat(view1.listView.selectedIndex).isEqualTo(1)
      assertThat(view2.listView.selectedIndex).isLessThan(0)

      view2.listView.selectedIndex = 2

      assertThat(view1.listView.selectedIndex).isLessThan(0)
      assertThat(view2.listView.selectedIndex).isEqualTo(2)
    }

  companion object {
    private const val STACK_STRING_A =
      "com.example.FakeA.func1(FakeA.java:123)\n" +
        "com.example.FakeA.func2(FakeA.java:456)\n" +
        "com.example.FakeA.func3(FakeA.java:789)\n"

    private const val STACK_STRING_B =
      "com.example.FakeB.func1(FakeB.java:123)\n" +
        "com.example.FakeB.func2(FakeB.java:456)\n" +
        "com.example.FakeB.func3(FakeB.java:789)\n" +
        "com.example.FakeB.func4(FakeB.java:1011)\n"
  }
}
