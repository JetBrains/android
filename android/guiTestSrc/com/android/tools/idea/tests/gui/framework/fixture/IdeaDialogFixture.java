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
import org.fest.reflect.exception.ReflectionError;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickCancelButton;
import static junit.framework.Assert.assertNotNull;
import static org.fest.reflect.core.Reflection.field;

public abstract class IdeaDialogFixture<T extends DialogWrapper> extends ComponentFixture<JDialog> {
  @NotNull private final T myDialogWrapper;

  @Nullable
  protected static <T extends DialogWrapper> T getDialogWrapperFrom(@NotNull JDialog dialog, Class<T> dialogWrapperType) {
    try {
      WeakReference<DialogWrapper> dialogWrapperRef = field("myDialogWrapper").ofType(new TypeRef<WeakReference<DialogWrapper>>() {})
                                                                              .in(dialog)
                                                                              .get();
      assertNotNull(dialogWrapperRef);
      DialogWrapper wrapper = dialogWrapperRef.get();
      if (dialogWrapperType.isInstance(wrapper)) {
        return dialogWrapperType.cast(wrapper);
      }
    }
    catch (ReflectionError ignored) {
    }
    return null;
  }

  protected IdeaDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull T dialogWrapper) {
    super(robot, target);
    myDialogWrapper = dialogWrapper;
  }

  @NotNull
  protected T getDialogWrapper() {
    return myDialogWrapper;
  }

  public void clickCancel() {
    findAndClickCancelButton(this);
  }
}
