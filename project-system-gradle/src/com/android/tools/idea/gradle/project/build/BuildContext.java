/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build;

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildContext {
  @NotNull private final GradleBuildInvoker.Request myRequest;

  public BuildContext(@NotNull GradleBuildInvoker.Request request) {
    myRequest = request;
  }

  @NotNull
  public Project getProject() {
    return myRequest.getProject();
  }

  @NotNull
  public List<String> getGradleTasks() {
    return myRequest.getGradleTasks();
  }

  @Nullable
  public BuildMode getBuildMode() {
    return myRequest.getMode();
  }
}
