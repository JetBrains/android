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

import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.DeviceTarget;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link DeployTarget} corresponds to an object that can either provide a custom {@link RunProfileState} and manage its own
 * launch of a run configuration, or provides a {@link DeviceTarget} in order to perform a regular launch on a locally connected device.
 */
public abstract class DeployTarget<S extends DeployTargetState> {
  private static ExtensionPointName<DeployTarget> EP_NAME = ExtensionPointName.create("com.android.run.deployTarget");
  private static List<DeployTarget> ourTargets;

  public static List<DeployTarget> getDeployTargets() {
    if (ourTargets == null) {
      ourTargets = Arrays.asList(EP_NAME.getExtensions());
    }
    return ourTargets;
  }

  @NotNull
  public abstract String getId();

  @NotNull
  public abstract String getDisplayName();

  @NotNull
  public abstract S createState();

  public abstract DeployTargetConfigurable<S> createConfigurable(@NotNull Project project,
                                                                 Disposable parentDisposable,
                                                                 @NotNull DeployTargetConfigurableContext ctx);

  public boolean isApplicable(boolean isTestConfig) {
    return true;
  }

  public boolean hasCustomRunProfileState(@NotNull Executor executor) {
    return false;
  }

  public RunProfileState getRunProfileState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env, @NotNull S state)
    throws ExecutionException {
    throw new IllegalStateException();
  }

  /**
   * @return the target to use, or null if the user cancelled (or there was an error). Null return values will end the launch quietly -
   * if an error needs to be displayed, the target chooser should surface it.
   */
  @Nullable
  public abstract DeviceTarget getTarget(@NotNull S state,
                                         @NotNull AndroidFacet facet,
                                         @NotNull DeviceCount deviceCount,
                                         boolean debug,
                                         @NotNull String runConfigName,
                                         @NotNull ConsolePrinter printer);

  public static class Renderer extends ColoredListCellRenderer<DeployTarget> {
    @Override
    protected void customizeCellRenderer(JList list, DeployTarget value, int index, boolean selected, boolean hasFocus) {
      append(value.getDisplayName());
    }
  }
}
