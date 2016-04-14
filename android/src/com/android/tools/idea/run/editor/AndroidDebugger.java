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

import com.android.ddmlib.Client;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public interface AndroidDebugger<S extends AndroidDebuggerState> {
  ExtensionPointName<AndroidDebugger> EP_NAME = ExtensionPointName.create("com.android.run.androidDebugger");

  @NotNull
  String getId();

  @NotNull
  String getDisplayName();

  @NotNull
  S createState();

  @NotNull
  AndroidDebuggerConfigurable<S> createConfigurable(@NotNull Project project);

  @NotNull
  DebugConnectorTask getConnectDebuggerTask(@NotNull ExecutionEnvironment env,
                                            @NotNull Set<String> applicationIds,
                                            @NotNull AndroidFacet facet,
                                            @NotNull S state,
                                            @NotNull String runConfigTypeId);

  boolean supportsProject(@NotNull Project project);

  void attachToClient(@NotNull Project project, @NotNull Client client);

  @NotNull
  Set<XBreakpointType<?, ?>> getSupportedBreakpointTypes();

  class Renderer extends ColoredListCellRenderer<AndroidDebugger> {
    @Override
    protected void customizeCellRenderer(JList list, AndroidDebugger debugger, int index, boolean selected, boolean hasFocus) {
      append(debugger.getDisplayName());
    }
  }
}
