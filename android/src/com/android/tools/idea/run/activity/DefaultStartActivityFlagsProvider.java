/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.ProfilerState;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultStartActivityFlagsProvider implements StartActivityFlagsProvider {

  @Nullable private final AndroidDebugger myAndroidDebugger;
  @Nullable private final AndroidDebuggerState myAndroidDebuggerState;
  @NotNull private final ProfilerState myProfilerState;
  private final boolean myWaitForDebugger;
  @NotNull private final String myExtraFlags;
  private final Project myProject;

  public DefaultStartActivityFlagsProvider(@Nullable AndroidDebugger androidDebugger,
                                           @Nullable AndroidDebuggerState androidDebuggerState,
                                           @NotNull Project project,
                                           boolean waitForDebugger,
                                           @NotNull String extraFlags) {
    this(androidDebugger, androidDebuggerState, new ProfilerState(), project, waitForDebugger, extraFlags);
  }

  public DefaultStartActivityFlagsProvider(@Nullable AndroidDebugger androidDebugger,
                                           @Nullable AndroidDebuggerState androidDebuggerState,
                                           @NotNull ProfilerState profilerState,
                                           @NotNull Project project,
                                           boolean waitForDebugger,
                                           @NotNull String extraFlags) {
    myAndroidDebugger = androidDebugger;
    myAndroidDebuggerState = androidDebuggerState;
    myProfilerState = profilerState;
    myWaitForDebugger = waitForDebugger;
    myExtraFlags = extraFlags;
    myProject = project;
  }

  @Override
  @NotNull
  public String getFlags(@NotNull IDevice device) {
    List<String> flags = Lists.newLinkedList();
    // The GAPID tracer requires the app to be started in debug mode.
    if (myWaitForDebugger || myProfilerState.isGapidEnabled()) {
      flags.add("-D");
    }
    if (!myExtraFlags.isEmpty()) {
      flags.add(myExtraFlags);
    }
    if (myWaitForDebugger && myAndroidDebugger != null) {
      String extraOptions = myAndroidDebugger.getAmStartOptions(myAndroidDebuggerState, myProject, device.getVersion());
      if (!extraOptions.isEmpty()) {
        flags.add(extraOptions);
      }
    }

    return StringUtil.join(flags, " ");
  }
}
