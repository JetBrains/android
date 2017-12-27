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

import com.intellij.openapi.fileEditor.TextEditor;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import static org.fest.util.Preconditions.checkNotNull;

public class TextEditorFixture {
  private final Robot myRobot;
  private final TextEditor myEditor;

  public TextEditorFixture(@NotNull Robot robot, @NotNull TextEditor editor) {
    myRobot = robot;
    myEditor = editor;
  }

  public TextEditor getEditor() {
    return myEditor;
  }

  public void focusAndWaitForFocusGain() {
    myRobot.focusAndWaitForFocusGain(checkNotNull(myEditor.getPreferredFocusedComponent()));
  }

  public int getOffset() {
    return GuiQuery.getNonNull(() -> myEditor.getEditor().getCaretModel().getOffset());
  }

  public void setOffset(int offset) {
    GuiTask.execute(() -> myEditor.getEditor().getCaretModel().moveToOffset(offset));
  }
}
