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
package com.android.tools.idea.tests.gui.framework.fixture.webp;

import com.android.tools.idea.rendering.webp.WebpPreviewDialog;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButtonWhenEnabled;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class WebpPreviewDialogFixture extends IdeaDialogFixture<WebpPreviewDialog> {

  @NotNull
  public static WebpPreviewDialogFixture findDialog(@NotNull Robot robot) {
    return findDialog(robot, Matchers.byTitle(JDialog.class, "Preview and Adjust Converted Images"));
  }

  @NotNull
  public static WebpPreviewDialogFixture findDialog(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new WebpPreviewDialogFixture(robot, find(robot, WebpPreviewDialog.class, matcher));
  }

  private WebpPreviewDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<WebpPreviewDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public WebpPreviewDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

  @NotNull
  public WebpPreviewDialogFixture clickNext() {
    findAndClickButtonWhenEnabled(this, "Next");
    return this;
  }

  @NotNull
  public WebpPreviewDialogFixture clickPrevious() {
    findAndClickButtonWhenEnabled(this, "Previous");
    return this;
  }

  @NotNull
  public WebpPreviewDialogFixture clickAcceptAll() {
    findAndClickButtonWhenEnabled(this, "Accept All");
    return this;
  }

  @NotNull
  public WebpPreviewDialogFixture clickFinish() {
    findAndClickButtonWhenEnabled(this, "Finish");
    return this;
  }
}
