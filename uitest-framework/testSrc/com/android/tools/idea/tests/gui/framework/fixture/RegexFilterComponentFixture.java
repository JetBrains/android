/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.logcat.RegexFilterComponent;
import com.intellij.ui.SearchTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;
import javax.swing.JCheckBox;
import java.awt.Container;

public class RegexFilterComponentFixture extends JComponentFixture<RegexFilterComponentFixture, RegexFilterComponent> {

  private SearchTextFieldFixture mySearchField;
  private JCheckBoxFixture myRegexCheckBox;

  public static RegexFilterComponentFixture find(Robot robot, Container root) {
    RegexFilterComponent searchField = robot.finder().findByType(root, RegexFilterComponent.class);
    return new RegexFilterComponentFixture(robot, searchField);
  }

  public RegexFilterComponentFixture(@NotNull Robot robot, @NotNull RegexFilterComponent component) {
    super(RegexFilterComponentFixture.class, robot, component);

    mySearchField = new SearchTextFieldFixture(robot, robot.finder().findByType(target(), SearchTextField.class));
    myRegexCheckBox = new JCheckBoxFixture(robot, robot.finder().findByType(target(), JCheckBox.class));
  }

  private void search(@NotNull String text) {
    mySearchField.deleteText().enterText(text + '\n');
  }

  public RegexFilterComponentFixture searchForText(String text) {
    myRegexCheckBox.deselect();
    search(text);
    return this;
  }

  public RegexFilterComponentFixture searchForRegex(String regex) {
    myRegexCheckBox.select();
    search(regex);
    return this;
  }

  public RegexFilterComponentFixture searchAgain() {
    mySearchField.enterText("\n");
    return this;
  }
}
