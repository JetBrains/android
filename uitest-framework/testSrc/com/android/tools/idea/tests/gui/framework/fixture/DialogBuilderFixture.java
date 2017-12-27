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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class DialogBuilderFixture extends IdeaDialogFixture<DialogWrapper> {

  protected DialogBuilderFixture(@NotNull Robot robot,
                                 @NotNull JDialog target,
                                 @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  @NotNull
  public static DialogBuilderFixture find(@NotNull Robot robot) {
    Ref<DialogWrapper> wrapperRef = new Ref<>();
    JDialog dialog = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
        if (wrapper != null) {
          String typeName = DialogBuilder.class.getName() + "$MyDialogWrapper";
          if (typeName.equals(wrapper.getClass().getName())) {
            wrapperRef.set(wrapper);
            return true;
          }
        }
        return false;
      }
    });
    return new DialogBuilderFixture(robot, dialog, wrapperRef.get());
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }
}
