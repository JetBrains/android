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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.tests.gui.framework.fixture.JComponentFixture;
import com.android.tools.idea.ui.resourcechooser.ResourceSwatchComponent;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StateListComponentFixture extends JComponentFixture<StateListComponentFixture, Box> {
  private final ResourceComponentFixture myResourceComponent;
  private final SwatchComponentFixture myAlphaComponent;

  public StateListComponentFixture(Robot robot, Box target) {
    super(StateListComponentFixture.class, robot, target);
    myResourceComponent = new ResourceComponentFixture(robot, robot.finder().findByType(target, ResourceComponent.class));

    ResourceSwatchComponent swatch =
      robot().finder().find(target(), new GenericTypeMatcher<ResourceSwatchComponent>(ResourceSwatchComponent.class) {
        @Override
        protected boolean isMatching(@NotNull ResourceSwatchComponent component) {
          return !(component.getParent() instanceof ResourceComponent);
        }
      });
    myAlphaComponent = new SwatchComponentFixture(robot(), swatch);
  }

  @NotNull
  public String getStateName() {
    return myResourceComponent.getLabelText();
  }

  @Nullable
  public String getValue() {
    return myResourceComponent.getValueString();
  }

  @NotNull
  public String getAlphaValue() {
    return myAlphaComponent.getText();
  }

  public boolean isAlphaVisible() {
    return myAlphaComponent.target().isVisible();
  }

  @NotNull
  public ResourceComponentFixture getValueComponent() {
    return myResourceComponent;
  }

  @NotNull
  public SwatchComponentFixture getAlphaComponent() {
    return myAlphaComponent;
  }
}
