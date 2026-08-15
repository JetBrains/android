/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.idea.blaze.android.cppimpl.debug.BlazeAutoAndroidDebugger;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/** Provides android debuggers and debugger states for blaze projects. */
public interface BlazeAndroidDebuggerService {

  static BlazeAndroidDebuggerService getInstance(Project project) {
    return ServiceManager.getService(project, BlazeAndroidDebuggerService.class);
  }

  /** Returns a different debugger depending on whether or not native debugging is required. */
  AndroidDebugger getDebugger(boolean isNativeDebuggingEnabled);

  /**
   * Returns fully initialized debugger states.
   *
   * <p>Note: Blaze projects should always use this method instead of the debuggers' {@link
   * AndroidDebugger#createState()} method. Blaze projects require additional setup such as
   * workspace directory flags that cannot be handled by the debuggers themselves.
   */
  AndroidDebuggerState getDebuggerState(AndroidDebugger debugger);

  /** Default debugger service. */
  class DefaultDebuggerService implements BlazeAndroidDebuggerService {
    private final Project project;

    public DefaultDebuggerService(Project project) {
      this.project = project;
    }

    @Override
    public AndroidDebugger getDebugger(boolean isNativeDebuggingEnabled) {
      return isNativeDebuggingEnabled ? new BlazeAutoAndroidDebugger() : new AndroidJavaDebugger();
    }

    @Override
    public AndroidDebuggerState getDebuggerState(AndroidDebugger debugger) {
      AndroidDebuggerState debuggerState = debugger.createState();
      if (isNdkPluginLoaded() && debuggerState instanceof NativeAndroidDebuggerState) {
        NativeAndroidDebuggerState nativeState = (NativeAndroidDebuggerState) debuggerState;

        // Source code is always relative to the workspace root in a blaze project.
        String workingDirPath = WorkspaceRoot.fromProject(project).directory().getPath();
        nativeState.setWorkingDir(workingDirPath);
      }
      return debuggerState;
    }
  }

  static boolean isNdkPluginLoaded() {
    return PluginManagerCore.getLoadedPlugins().stream()
        .anyMatch(
            d -> d.isEnabled() && d.getPluginId().getIdString().equals("com.android.tools.ndk"));
  }
}
