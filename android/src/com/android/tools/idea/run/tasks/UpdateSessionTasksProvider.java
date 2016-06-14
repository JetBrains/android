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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.InstantRunBuildAnalyzer;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UpdateSessionTasksProvider implements LaunchTasksProvider {
  private final InstantRunBuildAnalyzer myBuildAnalyzer;

  public UpdateSessionTasksProvider(@NotNull InstantRunBuildAnalyzer buildAnalyzer) {
    myBuildAnalyzer = buildAnalyzer;
  }

  @NotNull
  @Override
  public List<LaunchTask> getTasks(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    List<LaunchTask> tasks = new ArrayList<>(myBuildAnalyzer.getDeployTasks(null));
    tasks.add(myBuildAnalyzer.getNotificationTask());
    return ImmutableList.copyOf(tasks);
  }

  @Nullable
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull LaunchStatus launchStatus, @Nullable AndroidVersion version) {
    return null;
  }

  @Override
  public boolean createsNewProcess() {
    return false;
  }

  @Override
  public boolean monitorRemoteProcess() {
    // The return value doesn't matter as this is only applicable if #createNewProcess() returned true
    return true;
  }
}
