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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.ComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.ref.WeakReference;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.google.common.base.Strings.nullToEmpty;
import static org.fest.reflect.core.Reflection.field;
import static org.junit.Assert.assertNotNull;

public class MessageDialogFixture extends ComponentFixture<JDialog> {
  @NotNull
  public static MessageDialogFixture findByTitle(@NotNull Robot robot, @NotNull final String title) {
    JDialog dialog = waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        if (!title.equals(dialog.getTitle()) || !dialog.isShowing()) {
          return false;
        }
        WeakReference<DialogWrapper> dialogWrapperRef = field("myDialogWrapper").ofType(new TypeRef<WeakReference<DialogWrapper>>() {})
                                                                                .in(dialog)
                                                                                .get();
        DialogWrapper wrapper = dialogWrapperRef.get();
        assertNotNull(wrapper);
        String typeName = Messages.class.getName() + "$MessageDialog";
        return typeName.equals(wrapper.getClass().getName());
      }
    });
    return new MessageDialogFixture(robot, dialog);
  }

  private MessageDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(robot, target);
  }

  @NotNull
  public MessageDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

  public void clickCancel() {
    findAndClickCancelButton(this);
  }

  @NotNull
  public String getMessage() {
    final JTextPane textPane = robot.finder().findByType(target, JTextPane.class);
    return GuiActionRunner.execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return nullToEmpty(textPane.getText());
      }
    });
  }
}
