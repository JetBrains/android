/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Maps source directory when attaching to a process */
public class BlazeNativeDebuggerStateSourceMapping {
  public static void addSourceMapping(
      @NotNull Project project, @NotNull NativeAndroidDebuggerState state) {
    // Source code is always relative to the workspace root in a blaze project.
    String workingDirPath = WorkspaceRoot.fromProject(project).directory().getPath();
    state.setWorkingDir(workingDirPath);

    // Remote built binaries may use /proc/self/cwd to represent the working directory
    // so we manually map /proc/self/cwd to the workspace root.  We used to use
    // `plugin.symbol-file.dwarf.comp-dir-symlink-paths = "/proc/self/cwd"`
    // to automatically resolve this but it's no longer supported in newer versions of
    // LLDB.
    String sourceMapToWorkspaceRootCommand =
        "settings append target.source-map /proc/self/cwd " + workingDirPath;
    ImmutableList<String> startupCommands =
        ImmutableList.<String>builder()
            .addAll(state.getUserStartupCommands())
            .add(sourceMapToWorkspaceRootCommand)
            .build();
    state.setUserStartupCommands(startupCommands);
  }
}
