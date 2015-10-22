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

import com.android.tools.idea.run.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ShowChooserTarget extends DeployTarget<ShowChooserTarget.State> {
  public static final class State extends DeployTargetState {
    public boolean USE_LAST_SELECTED_DEVICE;
  }

  @NotNull
  @Override
  public String getId() {
    return TargetSelectionMode.SHOW_DIALOG.name();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Show Device Chooser Dialog";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @Override
  public DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                     Disposable parentDisposable,
                                                     @NotNull DeployTargetConfigurableContext context) {
    return new ShowChooserConfigurable();
  }

  @Nullable
  @Override
  public DeviceTarget getTarget(@NotNull State state,
                                @NotNull AndroidFacet facet,
                                @NotNull DeviceCount deviceCount,
                                boolean debug,
                                @NotNull String runConfigName,
                                @NotNull ConsolePrinter printer) {
    return new ManualTargetChooser(state, facet, runConfigName).getTarget(printer, deviceCount, debug);
  }

  private static class ShowChooserConfigurable implements DeployTargetConfigurable<State> {
    private final JBCheckBox myCheckbox;

    public ShowChooserConfigurable() {
      myCheckbox = new JBCheckBox("Use same device for future launches");
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      return myCheckbox;
    }

    @Override
    public void resetFrom(@NotNull State state, int uniqueID) {
      myCheckbox.setSelected(state.USE_LAST_SELECTED_DEVICE);
    }

    @Override
    public void applyTo(@NotNull State state, int uniqueID) {
      state.USE_LAST_SELECTED_DEVICE = myCheckbox.isSelected();
    }
  }
}
