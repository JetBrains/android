/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import java.awt.Point;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Button Action with a dropdown searchable popup menu.
 */
public class SearchableDropDownAction extends DropDownAction {
  public SearchableDropDownAction(@Nullable String title,
                                  @Nullable String description,
                                  @Nullable Icon icon) {
    super(title, description, icon);
  }

  @Override
  protected void showPopupMenu(@NotNull AnActionEvent eve, @NotNull ActionButton button) {
    JBPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, this, eve.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    popup.showUnderneathOf(button);
  }
}
