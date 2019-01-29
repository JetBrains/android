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
package com.android.tools.idea.tests.gui.framework.fixture.sdk;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SyncAndroidSdkDialogFixture extends IdeaDialogFixture<DialogWrapper> {

  @NotNull
  public static SyncAndroidSdkDialogFixture find(@NotNull Robot robot) {
    return new SyncAndroidSdkDialogFixture(robot, find(robot, DialogWrapper.class, Matchers.byTitle(JDialog.class, "Sync Android SDKs")));
  }

  private SyncAndroidSdkDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<DialogWrapper> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public void clickNo() {
    GuiTests.findAndClickButton(this, "No");
    waitUntilNotShowing(); // Mac dialogs have an animation, wait until it hides
  }
}
