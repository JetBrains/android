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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.ddmlib.Client;
import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.ndk.run.editor.NativeAndroidDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcessStarter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension of {@link NativeAndroidDebugger} with the following key differences compared to {@link
 * NativeAndroidDebugger}.
 *
 * <ul>
 *   <li>Overrides {@link #supportsProject} so native debugger is only enabled for native support is
 *       enabled.
 * </ul>
 */
public class BlazeNativeAndroidDebuggerBase extends NativeAndroidDebugger {
  /**
   * This ID needs to be lexicographically larger than "Java" so it come after the "Java" debugger
   * when sorted lexicographically in the "Attach Debugger to Android Process" dialog. See {@link
   * org.jetbrains.android.actions.AndroidProcessChooserDialog#populateDebuggerTypeCombo}.
   */
  public static final String ID = "Native" + Blaze.defaultBuildSystemName();

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return "Native Only";
  }

  @Override
  public boolean supportsProject(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null
        && blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C);
  }

  @Override
  public XDebugProcessStarter getDebugProcessStarterForExistingProcess(
      @NotNull Project project,
      @NotNull Client client,
      ApplicationProjectContext applicationContext,
      @Nullable NativeAndroidDebuggerState state) {
    if (state != null) {
      BlazeNativeDebuggerStateSourceMapping.addSourceMapping(project, state);
    }
    return super.getDebugProcessStarterForExistingProcess(
        project, client, applicationContext, state);
  }
}
