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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import java.awt.Container;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContentTabLabelFixture {
  @NotNull private final ContentTabLabel myContentTabLabel;
  @NotNull private final Robot robot;

  private ContentTabLabelFixture(@NotNull Robot robot, @NotNull ContentTabLabel label) {
    this.myContentTabLabel = label;
    this.robot = robot;
  }

  @NotNull
  public static ContentTabLabelFixture find(@NotNull Robot robot, @NotNull ComponentMatcher matcher) {
    return find(robot, matcher, 10);
  }

  @NotNull
  public static ContentTabLabelFixture find(@NotNull Robot robot, @NotNull ComponentMatcher matcher, long secondsToWait) {
    return find(robot, null, matcher, secondsToWait);
  }

  @NotNull
  public static ContentTabLabelFixture findByText(@NotNull Robot robot, @Nullable Container root, @NotNull String text, long secondsToWait) {
    return find(robot, root, Matchers.byText(ContentTabLabel.class, text), secondsToWait);
  }

    @NotNull
  public static ContentTabLabelFixture find(@NotNull Robot robot, @Nullable Container root, @NotNull ComponentMatcher matcher, long secondsToWait) {
    ContentTabLabel label = GuiTests.waitUntilShowing(robot, root, new GenericTypeMatcher<ContentTabLabel>(ContentTabLabel.class) {
      @Override
      protected boolean isMatching(@NotNull ContentTabLabel component) {
        return matcher.matches(component);
      }
    }, secondsToWait);
    return new ContentTabLabelFixture(robot, label);
  }

  @RunsInEDT
  public boolean isSelected() {
    return GuiQuery.getNonNull(() -> myContentTabLabel.isSelected());
  }

  @RunsInEDT
  public void click() {
    robot.click(myContentTabLabel);
  }

  public void close() {
    robot.rightClick(myContentTabLabel);
    JPopupMenuFixture contextMenuFixture = new JPopupMenuFixture(robot, robot.findActivePopupMenu());
    contextMenuFixture.menuItemWithPath("Close Tab").click();
  }
}
