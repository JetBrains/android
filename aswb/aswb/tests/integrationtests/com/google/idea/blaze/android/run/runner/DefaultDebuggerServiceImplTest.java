/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.ndk.run.editor.AutoAndroidDebuggerState;
import com.google.idea.blaze.android.cppimpl.debug.BlazeAutoAndroidDebugger;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDebuggerService.DefaultDebuggerService;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeAndroidDebuggerService.DefaultDebuggerService}. */
@RunWith(JUnit4.class)
public class DefaultDebuggerServiceImplTest extends BlazeIntegrationTestCase {
  @Test
  public void getDebuggerState_nativeDebugger_setsWorkspaceRootAndSourceRemap() {
    String workspaceRoot = WorkspaceRoot.fromProject(getProject()).directory().getPath();

    DefaultDebuggerService debuggerService = new DefaultDebuggerService(getProject());
    BlazeAutoAndroidDebugger nativeDebugger = new BlazeAutoAndroidDebugger();
    AutoAndroidDebuggerState state = nativeDebugger.createState();
    debuggerService.configureNativeDebugger(state, null);

    assertThat(state.getWorkingDir()).isEqualTo(workspaceRoot);
    assertThat(state.getUserStartupCommands())
        .contains("settings append target.source-map /proc/self/cwd/ " + workspaceRoot);
  }
}
