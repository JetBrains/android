/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.ComponentFinder;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.DialogFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.gradle.project.ChooseGradleHomeDialog.VALIDATION_MESSAGE_CLIENT_PROPERTY;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static junit.framework.Assert.*;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.swing.query.ComponentShowingQuery.isShowing;

public class ChooseGradleHomeDialogFixture extends ComponentFixture<ChooseGradleHomeDialogFixture, Dialog>
  implements ContainerFixture<Dialog> {
  @NotNull
  public static ChooseGradleHomeDialogFixture find(@NotNull final Robot robot) {
    DialogFixture found = findDialog(new GenericTypeMatcher<Dialog>(Dialog.class) {
      @Override
      protected boolean isMatching(@NotNull Dialog dialog) {
        if (!dialog.isVisible() || !dialog.isShowing()) {
          return false;
        }
        ComponentFinder finder = robot.finder();
        finder.find(dialog, new ComponentMatcher() {
          @Override
          public boolean matches(Component c) {
            if (c instanceof JBLabel) {
              return "Gradle home:".equals(((JBLabel)c).getText());
            }
            return false;
          }
        });
        finder.findByType(dialog, TextFieldWithBrowseButton.class);
        return true;
      }
    }).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(robot);
    return new ChooseGradleHomeDialogFixture(robot, found.target());
  }

  private ChooseGradleHomeDialogFixture(@NotNull Robot robot, @NotNull Dialog target) {
    super(ChooseGradleHomeDialogFixture.class, robot, target);
  }

  @NotNull
  public ChooseGradleHomeDialogFixture chooseGradleHome(@NotNull File gradleHomePath) {
    FixedSizeButton browseButton = robot().finder().findByType(target(), FixedSizeButton.class, true);
    robot().click(browseButton);

    FileChooserDialogFixture fileChooserDialog = FileChooserDialogFixture.findDialog(robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        Collection<JLabel> descriptionLabels = robot().finder().findAll(dialog, JLabelMatcher.withText("Gradle home:"));
        return descriptionLabels.size() == 1;
      }
    });

    VirtualFile toSelect = findFileByIoFile(gradleHomePath, true);
    assertNotNull(toSelect);

    fileChooserDialog.select(toSelect);
    fileChooserDialog.clickOk();

    return this;
  }

  @NotNull
  public ChooseGradleHomeDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

  public void clickCancel() {
    findAndClickCancelButton(this);
  }

  @NotNull
  public ChooseGradleHomeDialogFixture requireValidationError(@NotNull final String errorText) {
    Wait.minutes(2).expecting(String.format("error message '%1$s' to appear", errorText))
      .until(() -> {
        ComponentFinder finder = robot().finder();
        Collection<JPanel> errorTextPanels = finder.findAll(target(), new GenericTypeMatcher<JPanel>(JPanel.class) {
          @Override
          protected boolean isMatching(@NotNull JPanel panel) {
            // ErrorText is a private inner class
            return panel.isShowing() && panel.getClass().getSimpleName().endsWith("ErrorText");
          }
        });
        if (errorTextPanels.size() != 1) {
          return false;
        }
        JPanel errorTextPanel = getFirstItem(errorTextPanels);
        assertNotNull(errorTextPanel);
        Collection<JLabel> labels = finder.findAll(errorTextPanel, new GenericTypeMatcher<JLabel>(JLabel.class) {
          @Override
          protected boolean isMatching(@NotNull JLabel label) {
            String text = label.getText();
            return text != null && text.contains(errorText);
          }
        });
        return labels.size() == 1;
      });

    // The label with the error message above also has HTML formatting, which makes the check for error not 100% reliable.
    // To ensure that the shown error message is what we expect, we store the message as a client property in the dialog's
    // TextFieldWithBrowseButton component.
    TextFieldWithBrowseButton field = robot().finder().findByType(target(), TextFieldWithBrowseButton.class);
    Object actual = field.getClientProperty(VALIDATION_MESSAGE_CLIENT_PROPERTY);
    assertEquals("Error message", errorText, actual);

    return this;
  }

  public void requireNotShowing() {
    assertFalse("Dialog '" + target().getTitle() + "' is showing", isShowing(target()));
  }

  public void close() {
    robot().close(target());
  }
}
