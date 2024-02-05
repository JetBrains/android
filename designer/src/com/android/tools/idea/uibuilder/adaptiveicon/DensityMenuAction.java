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
package com.android.tools.idea.uibuilder.adaptiveicon;

import com.android.resources.Density;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class DensityMenuAction extends DropDownAction {
  private static final Density[] DENSITIES = new Density[] {Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH};
  private final NlModel myModel;

  public DensityMenuAction(@NotNull NlModel model) {
    super("Device Screen Density", "Device Screen Density", null);
    myModel = model;
    for (Density density : DENSITIES) {
      add(new SetDensityAction(myModel, density));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    int currentValue = myModel.getConfiguration().getDensity().getDpiValue();
    for (int i = 0; i < DENSITIES.length; i++) {
      if (DENSITIES[i].getDpiValue() >= currentValue || i == DENSITIES.length - 1) {
        e.getPresentation().setText(DENSITIES[i].getResourceValue());
        break;
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  private static class SetDensityAction extends AnAction {
    private final NlModel myModel;
    private final Density myDensity;

    private SetDensityAction(@NotNull NlModel model, @NotNull Density density) {
      super(density.getResourceValue());
      myModel = model;
      myDensity = density;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      NlModelHelperKt.overrideConfigurationDensity(myModel, myDensity);
    }
  }
}
