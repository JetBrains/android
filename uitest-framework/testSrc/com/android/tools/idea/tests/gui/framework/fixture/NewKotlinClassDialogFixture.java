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
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;
import static com.android.tools.idea.tests.gui.framework.matcher.Matchers.byType;

import com.intellij.ui.components.JBList;
import java.awt.event.KeyEvent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

public class NewKotlinClassDialogFixture extends ComponentFixture<NewKotlinClassDialogFixture, JRootPane> implements ContainerFixture<JRootPane> {

  public static NewKotlinClassDialogFixture find(IdeFrameFixture ideFrameFixture) {
    JLabel titleField = waitUntilShowing(ideFrameFixture.robot(), JLabelMatcher.withText("New Kotlin Class/File"));
    return new NewKotlinClassDialogFixture(ideFrameFixture, titleField.getRootPane());
  }

  private NewKotlinClassDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JRootPane rootPane) {
    super(NewKotlinClassDialogFixture.class, ideFrameFixture.robot(), rootPane);
  }

  @NotNull
  public NewKotlinClassDialogFixture enterName(@NotNull String name) {
    JTextField textField = robot().finder().findByType(target(), JTextField.class, true);
    new JTextComponentFixture(robot(), textField).setText(name);

    return this;
  }

  @NotNull
  public NewKotlinClassDialogFixture selectType(@NotNull String type) {
    JListFixture listFixture = new JListFixture(robot(), waitUntilShowingAndEnabled(robot(), target(), byType(JBList.class)));
    listFixture.selectItem(type);

    return this;
  }

  public void clickOk() {
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }
}
