/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public class ScrollToViewFocusListener extends FocusAdapter {
  private final JComponent myComponent;

  public ScrollToViewFocusListener(JComponent component) {
    myComponent = component;
  }

  @Override
  public void focusGained(@NotNull FocusEvent event) {
    JComponent parent = (JComponent)myComponent.getParent();
    parent.scrollRectToVisible(myComponent.getBounds());
  }
}
