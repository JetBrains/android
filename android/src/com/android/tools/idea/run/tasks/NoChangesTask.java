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
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NoChangesTask implements LaunchTask {
  private final Project myProject;
  private final InstantRunContext myContext;

  public NoChangesTask(@NotNull Project project, @NotNull InstantRunContext context) {
    myProject = project;
    myContext = context;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "";
  }

  @Override
  public int getDuration() {
    return 0;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    //noinspection ConstantConditions (build info cannot be null)
    List<InstantRunArtifact> artifacts = myContext.getInstantRunBuildInfo().getArtifacts();
    assert artifacts.isEmpty();

    InstantRunManager.LOG.info("No changes");
    return true;
  }
}
