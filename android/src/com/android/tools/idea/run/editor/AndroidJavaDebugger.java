/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AndroidJavaDebugger implements AndroidDebugger<AndroidDebuggerState> {

  public static final String ID = "Java";

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return getId();
  }

  @NotNull
  @Override
  public AndroidDebuggerState createState() {
    return new AndroidDebuggerState();
  }

  @NotNull
  @Override
  public AndroidDebuggerConfigurable<AndroidDebuggerState> createConfigurable(@NotNull Project project) {
    return new AndroidDebuggerConfigurable<AndroidDebuggerState>();
  }

  @NotNull
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull ExecutionEnvironment env,
                                                   @NotNull Set<String> applicationIds,
                                                   @NotNull AndroidFacet facet,
                                                   @NotNull AndroidDebuggerState state,
                                                   @NotNull String runConfigTypeId) {
    return new ConnectJavaDebuggerTask(env.getProject(), applicationIds);
  }
}
