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

import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;

public class JTextComponentWithHtmlFixture extends JTextComponentFixture {
  @NotNull
  public static JTextComponentWithHtmlFixture create(@NotNull Robot robot, @NotNull JTextComponent target) {
    return new JTextComponentWithHtmlFixture(robot, target);
  }

  private JTextComponentWithHtmlFixture(@NotNull Robot robot, @NotNull JTextComponent target) {
    super(robot, target);
  }

  public void clickOnLink(@NotNull String content) throws BadLocationException {
    JTextComponent pane = target();
    if (!(pane.getDocument() instanceof HTMLDocument)) {
      throw new RuntimeException("This JTextComponent doesn't contain HTML");
    }
    HTMLDocument doc = (HTMLDocument)pane.getDocument();
    String text = doc.getText(0, doc.getLength() + 1);
    int position = text.indexOf(content);
    if (position < 0) {
      throw new RuntimeException("The HTML in the JTextComponent doesn't contain: " + content);
    }
    AttributeSet attributes = doc.getCharacterElement(position).getAttributes();
    AttributeSet anchors = (AttributeSet)attributes.getAttribute(HTML.Tag.A);
    if (anchors == null) {
      throw new RuntimeException("There are no anchors around the text: " + content);
    }
    Rectangle bounds = target().modelToView(position);
    Point pos = new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    scrollRectToVisible(bounds);
    robot().waitForIdle();
    robot().click(target(), pos, MouseButton.LEFT_BUTTON, 1);
  }

  private void scrollRectToVisible(Rectangle bounds) {
    GuiTask.execute(() -> {
      target().scrollRectToVisible(bounds);
    });
  }
}
