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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class RenameModuleDialogFixture extends IdeaDialogFixture<DialogWrapper> {

  @NotNull private final IdeFrameFixture ideFrameFixture;

  @NotNull
  public static RenameModuleDialogFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    final Ref<DialogWrapper> wrapperRef = new Ref<>();
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Rename Module").and(
      new GenericTypeMatcher<JDialog>(JDialog.class) {
        @Override
        protected boolean isMatching(@NotNull JDialog dialog) {
          DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
          if (wrapper != null) {
            String typeName = Messages.class.getName() + "$InputDialog";
            if (typeName.equals(wrapper.getClass().getName())) {
              wrapperRef.set(wrapper);
              return true;
            }
          }
          return false;
        }
      }));
    return new RenameModuleDialogFixture(ideFrameFixture, dialog, wrapperRef.get());
  }

  @NotNull
  public RenameModuleDialogFixture enterText(@NotNull String text) {
    JTextComponent input = robot().finder().find(target(), JTextComponentMatcher.any());
    new JTextComponentFixture(robot(), input).enterText(text);
    return this;
  }

  @NotNull
  public IdeFrameFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    Wait.seconds(1).expecting(target().getTitle() + " dialog to disappear").until(() -> !target().isShowing());
    return ideFrameFixture;
  }

  @NotNull
  public RenameModuleDialogFixture clickOkAndRequireError(@NotNull String message) {
    GuiTests.findAndClickOkButton(this);
    MessagesFixture.findByTitle(robot(), "Rename Module").requireMessageContains(message).clickOk();
    return this;
  }

  private RenameModuleDialogFixture(
    @NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog target, @NotNull DialogWrapper dialogWrapper) {
    super(ideFrameFixture.robot(), target, dialogWrapper);
    this.ideFrameFixture = ideFrameFixture;
  }
}
