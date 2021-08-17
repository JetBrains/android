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
package com.android.tools.inspectors.common.api.ide.stacktrace;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel;
import com.intellij.testFramework.ProjectRule;
import org.junit.Rule;
import org.junit.Test;

public class IntelliJStackTraceGroupTest {
  private static final String STACK_STRING_A =
    "com.example.FakeA.func1(FakeA.java:123)\n" +
    "com.example.FakeA.func2(FakeA.java:456)\n" +
    "com.example.FakeA.func3(FakeA.java:789)\n";

  private static final String STACK_STRING_B =
    "com.example.FakeB.func1(FakeB.java:123)\n" +
    "com.example.FakeB.func2(FakeB.java:456)\n" +
    "com.example.FakeB.func3(FakeB.java:789)\n" +
    "com.example.FakeB.func4(FakeB.java:1011)\n";

  @Rule
  public final ProjectRule myProjectRule = new ProjectRule();

  @Test
  public void canOnlySelectOneStackTraceViewAtATime() {
    IntelliJStackTraceGroup group = new IntelliJStackTraceGroup(
      myProjectRule.getProject(),
      (IntelliJStackTraceViewTest::createStackTraceView));

    StackTraceModel model1 = IntelliJStackTraceViewTest.createStackTraceModel();
    StackTraceModel model2 = IntelliJStackTraceViewTest.createStackTraceModel();

    // IntelliJStackTraceGroup always creates IntelliJStackTraceView instances
    IntelliJStackTraceView view1 = (IntelliJStackTraceView)group.createStackView(model1);
    IntelliJStackTraceView view2 = (IntelliJStackTraceView)group.createStackView(model2);

    model1.setStackFrames(STACK_STRING_A);
    model2.setStackFrames(STACK_STRING_B);

    assertThat(view1.getListView().getSelectedIndex()).isLessThan(0);
    assertThat(view2.getListView().getSelectedIndex()).isLessThan(0);

    view1.getListView().setSelectedIndex(1);

    assertThat(view1.getListView().getSelectedIndex()).isEqualTo(1);
    assertThat(view2.getListView().getSelectedIndex()).isLessThan(0);

    view2.getListView().setSelectedIndex(2);

    assertThat(view1.getListView().getSelectedIndex()).isLessThan(0);
    assertThat(view2.getListView().getSelectedIndex()).isEqualTo(2);
  }
}
