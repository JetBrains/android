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
package com.android.tools.idea.actions;

import static com.android.tools.idea.actions.DesignerDataKeys.CONFIGURATIONS;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.configurations.AdaptiveIconShape;
import com.android.tools.configurations.Configuration;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * Action for changing the shape of the adaptive icon when previewing
 */
public class ShapeMenuAction extends DropDownAction {

  public ShapeMenuAction() {
    super("Adaptive Icon Shape", "Adaptive Icon Shape", null);
    for (AdaptiveIconShape shape : AdaptiveIconShape.values()) {
      add(new SetShapeAction(shape));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
    if (configurations == null) {
      return;
    }
    Configuration configuration = Iterables.getFirst(configurations, null);
    AdaptiveIconShape shape = configuration != null ? configuration.getAdaptiveShape() : AdaptiveIconShape.getDefaultShape();
    e.getPresentation().setText(shape.getName());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  private static class SetShapeAction extends ConfigurationAction {
    private final AdaptiveIconShape myShape;

    private SetShapeAction(@NotNull AdaptiveIconShape shape) {
      super(shape.getName());
      myShape = shape;
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setAdaptiveShape(myShape);
    }
  }
}
