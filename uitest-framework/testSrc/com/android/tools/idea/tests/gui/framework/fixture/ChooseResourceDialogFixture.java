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
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.google.common.collect.Iterables;
import com.intellij.ui.SearchTextField;
import java.awt.Component;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.TypeMatcher;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ChooseResourceDialogFixture extends IdeaDialogFixture<ChooseResourceDialog> {
  @NotNull
  public static ChooseResourceDialogFixture find(@NotNull Robot robot) {
    return new ChooseResourceDialogFixture(robot, find(robot, ChooseResourceDialog.class));
  }

  @NotNull
  public static ChooseResourceDialogFixture find(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new ChooseResourceDialogFixture(robot, find(robot, ChooseResourceDialog.class, matcher));
  }

  private ChooseResourceDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ChooseResourceDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public JTextComponentFixture getSearchField() {
    Component component = robot().finder().find(target(), new TypeMatcher(SearchTextField.class));
    return new JTextComponentFixture(robot(), ((SearchTextField)component).getTextEditor());
  }

  @NotNull
  public String getError() {
    return waitUntilShowing(robot(), waitForErrorPanel(), Matchers.byType(JLabel.class)).getText();
  }

  @NotNull
  public JPanel waitForErrorPanel() {
    return waitUntilShowing(robot(), new GenericTypeMatcher<JPanel>(JPanel.class) {
        @Override
        protected boolean isMatching(@NotNull JPanel component) {
          return ("com.intellij.openapi.ui.DialogWrapper$ErrorText").equals(component.getClass().getName());
        }
      });
  }

  @NotNull
  public JListFixture getList(@NotNull String appNamespaceLabel, int index) {
    AtomicReference<JListFixture> list = new AtomicReference<>();
    Wait.seconds(10).expecting("Find list " + appNamespaceLabel + "@" + index).until(() -> {
      Collection<JList> lists = robot().finder().findAll(target(), Matchers.byName(JList.class, appNamespaceLabel));
      if (index >= lists.size()) {
        return false;
      }

      list.set(new JListFixture(robot(), Iterables.get(lists, index)));
      return true;
    });

    return list.get();
  }

  @NotNull
  public JListFixture getList(@NotNull String appNamespaceLabel) {
    return new JListFixture(robot(), waitUntilFound(robot(), target(), Matchers.byName(JList.class, appNamespaceLabel)));
  }

  @NotNull
  public ChooseResourceDialogFixture expandList(@NotNull String appNamespaceLabel) {
    new JLabelFixture(robot(), waitUntilShowing(robot(), target(), Matchers.byText(JLabel.class, appNamespaceLabel))).click();
    return this;
  }

  public void clickOK() {
    findAndClickOkButton(this);
  }
}
