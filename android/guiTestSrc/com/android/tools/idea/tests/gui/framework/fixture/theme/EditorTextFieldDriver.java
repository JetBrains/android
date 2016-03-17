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

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.EditorTextField;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.driver.TextDisplayDriver;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.util.regex.Pattern;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EditorTextFieldDriver extends JComponentDriver implements TextDisplayDriver<EditorTextField> {
  /**
   * Creates a new {@link EditorTextFieldDriver}.
   *
   * @param robot the robot the robot to use to simulate user input.
   */
  public EditorTextFieldDriver(Robot robot) {
    super(robot);
  }

  @RunsInEDT
  @Override
  public void requireText(@NotNull EditorTextField component, @Nullable String expected) {
    assertEquals(expected, textOf(component));
  }

  @RunsInEDT
  @Override
  public void requireText(@NotNull EditorTextField component, @NotNull Pattern pattern) {
    assertTrue(Strings.match(pattern, textOf(component)));
  }

  @RunsInEDT
  @Nullable
  @Override
  public String textOf(@NotNull final EditorTextField component) {
    return execute(new GuiQuery<String>() {
      @Nullable
      @Override
      protected String executeInEDT() throws Throwable {
        return component.getText();
      }
    });
  }

  @RunsInEDT
  public void enterText(@NotNull EditorTextField component, @NotNull String text) {
    focusAndWaitForFocusGain(component);
    robot.enterText(text);
  }

  @RunsInEDT
  public void deleteText(@NotNull EditorTextField component) {
    selectAll(component);
    robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
  }

  @RunsInEDT
  public void replaceText(@NotNull EditorTextField component, @NotNull String text) {
    selectAll(component);
    robot.enterText(text);
  }

  @RunsInEDT
  public void selectAll(@NotNull final EditorTextField component) {
    selectText(component, 0, component.getText().length());
  }

  @RunsInEDT
  public void selectText(@NotNull final EditorTextField component, final int start, final int end) {
    if (!component.isFocusOwner()) {
      focusAndWaitForFocusGain(component);
    }
    execute(new GuiQuery<Void>() {
      @Nullable
      @Override
      protected Void executeInEDT() throws Throwable {
        Editor editor = component.getEditor();
        assert editor!= null;
        Caret caret = editor.getCaretModel().getCurrentCaret();
        caret.setSelection(start, end);
        return null;
      }
    });
  }
}
