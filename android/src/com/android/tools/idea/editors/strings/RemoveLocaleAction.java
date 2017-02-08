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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class RemoveLocaleAction extends AnAction {
  private final JTable myTable;
  private final AndroidFacet myFacet;

  RemoveLocaleAction(@NotNull JTable table, @NotNull AndroidFacet facet) {
    super("Remove Locale", null, AndroidIcons.Globe);

    myTable = table;
    myFacet = facet;
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabled(!((StringResourceTableModel)myTable.getModel()).getLocales().isEmpty());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    JList list = new LocaleList(((StringResourceTableModel)myTable.getModel()).getLocales());

    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setItemChoosenCallback(() -> StringsWriteUtils.removeLocale((Locale)list.getSelectedValue(), myFacet, this))
      .createPopup();

    popup.showUnderneathOf(event.getInputEvent().getComponent());
  }
}
