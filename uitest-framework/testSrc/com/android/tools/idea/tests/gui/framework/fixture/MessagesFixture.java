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
import com.intellij.openapi.util.JDOMUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.google.common.truth.Truth.assertThat;

public class MessagesFixture {
  @NotNull private final ContainerFixture<? extends Container> myDelegate;
  @NotNull private final JDialog myDialog; // Mac changes the panel window before closing animation. We keep a reference here.

  @NotNull
  public static MessagesFixture findByTitle(@NotNull Robot robot, @NotNull String title) {
        return findByTitle(robot, title, 10);
  }

  @NotNull
  public static MessagesFixture findByTitle(@NotNull Robot robot, @NotNull String title, long secondsToWait) {
    MessageDialogFixture dialog = MessageDialogFixture.findByTitle(robot, title, secondsToWait);
    return new MessagesFixture(dialog, dialog.target());
  }

  private MessagesFixture(@NotNull ContainerFixture<? extends Container> delegate, @NotNull JDialog dialog) {
    myDelegate = delegate;
    myDialog = dialog;
  }

  @NotNull
  public MessagesFixture clickOk() {
    findAndClickOkButton(myDelegate);
    waitUntilNotShowing();
    return this;
  }

  @NotNull
  public MessagesFixture clickYes() {
    return click("Yes");
  }

  @NotNull
  public MessagesFixture click(@NotNull String text) {
    findAndClickButton(myDelegate, text);
    waitUntilNotShowing();
    return this;
  }

  @NotNull
  public MessagesFixture requireMessageContains(@NotNull String message) {
    String actual = ((Delegate)myDelegate).getMessage();
    assertThat(actual).contains(message);
    return this;
  }

  public void clickCancel() {
    findAndClickCancelButton(myDelegate);
    waitUntilNotShowing();
  }

  private void waitUntilNotShowing() {
    Wait.seconds(1).expecting("not showing").until(() -> !myDialog.isShowing());
  }

  @Nullable
  public <T extends JComponent> T find(GenericTypeMatcher<T> matcher) {
    return myDelegate.robot().finder().find(myDelegate.target(), matcher);
  }

  interface Delegate {
    @NotNull String getMessage();
  }

  @Nullable
  private static String getHtmlBody(@NotNull String html) {
    try {
      String sheetTitle = JDOMUtil.load(html).getChild("body").getText();
      return sheetTitle.replace("\n", "").trim();
    }
    catch (Throwable e) {
      Logger.getInstance(MessagesFixture.class).info("Failed to parse HTML '" + html + "'", e);
    }
    return null;
  }
}
