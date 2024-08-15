/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.state;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DebuggerSettingsState}. */
@RunWith(JUnit4.class)
public class DebuggerSettingsStateTest {
  @Test
  public void testNativeDebuggingOptionRetainedAfterEditorReset() {
    DebuggerSettingsState state = new DebuggerSettingsState(true);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);
    assertThat(state.isNativeDebuggingEnabled()).isTrue();

    state.setNativeDebuggingEnabled(false);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);
    assertThat(state.isNativeDebuggingEnabled()).isFalse();
  }
}
