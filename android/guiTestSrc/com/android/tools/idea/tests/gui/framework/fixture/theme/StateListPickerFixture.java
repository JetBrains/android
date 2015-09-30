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

import com.android.tools.idea.editors.theme.StateListPicker;
import com.google.common.collect.Lists;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class StateListPickerFixture extends JPanelFixture {
  public StateListPickerFixture(Robot robot, StateListPicker target) {
    super(robot, target);
  }

  public List<StateListComponentFixture> getStateComponents() {
    List<StateListComponentFixture> stateComponents = Lists.newArrayList();
    Collection<Box> states =
      robot().finder().findAll(target(), new GenericTypeMatcher<Box>(Box.class) {
        @Override
        protected boolean isMatching(@NotNull Box component) {
          return target().equals(component.getParent());
        }
      });
    for (Box state : states) {
      stateComponents.add(new StateListComponentFixture(robot(), state));
    }
    return stateComponents;
  }
}
