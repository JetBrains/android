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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import java.util.Arrays;
import java.util.List;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DeployTargetProvider {
  @VisibleForTesting
  public static final ExtensionPointName<DeployTargetProvider> EP_NAME = ExtensionPointName.create("com.android.run.deployTargetProvider");

  private static List<DeployTargetProvider> ourTargets;

  @NotNull
  public static List<DeployTargetProvider> getProviders() {
    if (ourTargets == null) {
      ourTargets = Arrays.stream(EP_NAME.getExtensions()).filter(DeployTargetProvider::isEnabled).toList();
    }

    return ourTargets;
  }

  public boolean isEnabled() {
    return true;
  }

  @NotNull
  public abstract String getId();

  @NotNull
  public abstract String getDisplayName();

  @NotNull
  public abstract DeployTargetState createState();

  protected boolean isApplicable(boolean testConfiguration) {
    return true;
  }

  public abstract DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                              @NotNull Disposable parentDisposable,
                                                              @NotNull DeployTargetConfigurableContext ctx);

  /**
   * Returns whether the current deploy target needs to ask for user input on every launch.
   * If this method is overridden to return true, then {@link #showPrompt} must also be overridden.
   */
  public boolean requiresRuntimePrompt(@NotNull Project project) {
    return false;
  }

  /**
   * Prompt the user for whatever input might be required at the time of the launch and return a customized {@link DeployTarget}.
   * A return value of null indicates that the launch should be canceled.
   *
   * @param project
   */
  @Nullable
  public DeployTarget showPrompt(@NotNull Project project) {
    throw new IllegalStateException();
  }

  @NotNull
  public abstract DeployTarget getDeployTarget(@NotNull Project project);

  public abstract boolean canDeployToLocalDevice();

  public static class Renderer extends ColoredListCellRenderer<DeployTargetProvider> {
    @Override
    protected void customizeCellRenderer(@NotNull JList list, DeployTargetProvider value, int index, boolean selected, boolean hasFocus) {
      append(value.getDisplayName());
    }
  }
}
