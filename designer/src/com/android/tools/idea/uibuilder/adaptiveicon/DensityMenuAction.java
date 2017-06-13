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
import com.android.tools.idea.configurations.FlatComboAction;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

public class DensityMenuAction extends FlatComboAction {
  private static final Density[] DENSITIES = new Density[] {Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH};
  private final NlModel myModel;

  public DensityMenuAction(@NotNull NlModel model) {
    myModel = model;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Device Screen Density");
    presentation.setIcon(EmptyIcon.ICON_0);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    int currentValue = myModel.getConfiguration().getDensity().getDpiValue();
    for (int i = 0; i < DENSITIES.length; i++) {
      if (DENSITIES[i].getDpiValue() >= currentValue || i == DENSITIES.length - 1) {
        e.getPresentation().setText(DENSITIES[i].getResourceValue());
        break;
      }
    }
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup(null, true);
    for (Density density : DENSITIES) {
      group.add(new SetDensityAction(myModel, density));
    }
    return group;
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
    public void actionPerformed(AnActionEvent e) {
      NlModelHelperKt.overrideConfigurationDensity(myModel, myDensity);
    }
  }
}
