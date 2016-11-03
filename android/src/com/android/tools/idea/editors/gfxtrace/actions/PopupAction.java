/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * {@link AnAction} that shows a popup next to the action's toolbar button when activated.
 */
public abstract class PopupAction extends AnAction {
  public PopupAction() {
  }

  public PopupAction(Icon icon) {
    super(icon);
  }

  public PopupAction(@Nullable String text) {
    super(text);
  }

  public PopupAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    JComponent contents = getPopupContents(e);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(contents, contents)
      .setCancelOnClickOutside(true)
      .setResizable(false)
      .setMovable(false)
      .createPopup();

    Component source = e.getInputEvent().getComponent();
    popup.setMinimumSize(new Dimension(0, source.getHeight()));
    popup.show(new RelativePoint(source, new Point(source.getWidth(), 0)));
  }

  protected abstract JComponent getPopupContents(AnActionEvent e);
}
