/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.intellij.openapi.wm.impl.content;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.FluentMatcher;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

public class ContentTabLabelFixture {
  private final ContentTabLabel myContentTabLabel;
  private final Robot robot;

  private ContentTabLabelFixture(@NotNull Robot robot, @NotNull ContentTabLabel label) {
    this.myContentTabLabel = label;
    this.robot = robot;
  }

  @NotNull
  public static ContentTabLabelFixture find(@NotNull Robot robot, @NotNull ComponentMatcher matcher) {
    ContentTabLabel label = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<ContentTabLabel>(ContentTabLabel.class) {
      @Override
      protected boolean isMatching(@NotNull ContentTabLabel component) {
        return matcher.matches(component);
      }
    });
    return new ContentTabLabelFixture(robot, label);
  }

  @RunsInEDT
  public boolean isSelected() {
    return GuiQuery.getNonNull(() -> myContentTabLabel.isSelected());
  }
}
