/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.idea.blaze.android.run;

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerInfoProvider;
import com.google.idea.blaze.android.cppimpl.debug.BlazeNativeAndroidDebugger;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestRunConfigurationState;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Provider of blaze project compatible android debuggers. */
public class BlazeCommandAndroidDebuggerInfoProvider implements AndroidDebuggerInfoProvider {
  @Override
  public boolean supportsProject(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null;
  }

  @Override
  @SuppressWarnings("rawtypes") // List includes multiple AndroidDebuggerState types.
  public List<AndroidDebugger> getAndroidDebuggers(RunConfiguration configuration) {
    if (getCommonState(configuration) != null) {
      return Arrays.asList(new BlazeNativeAndroidDebugger(), new AndroidJavaDebugger());
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public AndroidDebugger<AndroidDebuggerState> getSelectedAndroidDebugger(
      RunConfiguration configuration) {
    // b/170159822 Always return java debugger because BlazeAutoAndroidDebugger doesn't work and
    //             users likely want the java debugger not the native debugger.
    return new AndroidJavaDebugger();
  }

  @Nullable
  @Override
  public AndroidDebuggerState getSelectedAndroidDebuggerState(RunConfiguration configuration) {
    AndroidDebugger<AndroidDebuggerState> debugger = getSelectedAndroidDebugger(configuration);
    if (debugger == null) {
      return null;
    }
    return debugger.createState();
  }

  @Nullable
  private BlazeAndroidRunConfigurationCommonState getCommonState(RunConfiguration configuration) {
    if (!(configuration instanceof BlazeCommandRunConfiguration)) {
      return null;
    }
    BlazeCommandRunConfiguration blazeRunConfig = (BlazeCommandRunConfiguration) configuration;
    BlazeAndroidBinaryRunConfigurationState binaryState =
        blazeRunConfig.getHandlerStateIfType(BlazeAndroidBinaryRunConfigurationState.class);
    if (binaryState != null) {
      return binaryState.getCommonState();
    }
    BlazeAndroidTestRunConfigurationState testState =
        blazeRunConfig.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (testState != null) {
      return testState.getCommonState();
    }
    return null;
  }
}
