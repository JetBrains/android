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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.messages.SheetController;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickCancelButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.intellij.openapi.util.JDOMUtil.loadDocument;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;

public class MessagesFixture {
  @NotNull private final ComponentFixture<? extends Container> myDelegate;

  @NotNull
  public static MessagesFixture findByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
    if (Messages.canShowMacSheetPanel()) {
      return new MessagesFixture(findMacSheetByTitle(robot, root, title));
    }
    MessageDialogFixture dialog = MessageDialogFixture.findByTitle(robot, title);
    return new MessagesFixture(dialog);
  }

  private MessagesFixture(@NotNull ComponentFixture<? extends Container> delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public MessagesFixture clickOk() {
    findAndClickOkButton(myDelegate);
    return this;
  }

  public void clickCancel() {
    findAndClickCancelButton(myDelegate);
  }

  @NotNull
  static JPanelFixture findMacSheetByTitle(@NotNull Robot robot, @NotNull Container root, @NotNull String title) {
    JPanel sheetPanel = robot.finder().find(root, new GenericTypeMatcher<JPanel>(JPanel.class) {
      @Override
      protected boolean isMatching(JPanel panel) {
        if (panel.getClass().getName().startsWith(SheetController.class.getName()) && panel.isShowing()) {
          SheetController controller = findSheetController(panel);
          JPanel sheetPanel = field("mySheetPanel").ofType(JPanel.class).in(controller).get();
          if (sheetPanel == panel) {
            return true;
          }
        }
        return false;
      }
    });

    String sheetTitle = getTitle(sheetPanel, robot);
    assertThat(sheetTitle).as("Sheet title").isEqualTo(title);

    return new JPanelFixture(robot, sheetPanel);
  }

  @Nullable
  private static String getTitle(@NotNull JPanel sheetPanel, @NotNull Robot robot) {
    SheetController sheetController = findSheetController(sheetPanel);
    final JEditorPane messageTextPane = field("messageTextPane").ofType(JEditorPane.class).in(sheetController).get();

    JEditorPane titleTextPane = robot.finder().find(sheetPanel, new GenericTypeMatcher<JEditorPane>(JEditorPane.class) {
      @Override
      protected boolean isMatching(JEditorPane editorPane) {
        return editorPane != messageTextPane;
      }
    });

    String htmlText = titleTextPane.getText();
    if (isNotEmpty(htmlText)) {
      try {
        Document document = loadDocument(htmlText);
        Element rootElement = document.getRootElement();
        String sheetTitle = rootElement.getChild("body").getText();
        return sheetTitle.replace("\n", "").trim();
      }
      catch (Throwable e) {
        Logger.getInstance(MessagesFixture.class).info("Failed to read sheet title", e);
      }
    }
    return null;
  }

  @NotNull
  private static SheetController findSheetController(@NotNull JPanel sheetPanel) {
    return field("this$0").ofType(SheetController.class).in(sheetPanel).get();
  }
}
