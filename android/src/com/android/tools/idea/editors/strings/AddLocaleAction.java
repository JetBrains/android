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

import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Optional;

final class AddLocaleAction extends AnAction {
  private final StringResourceTable myTable;
  private final AndroidFacet myFacet;

  AddLocaleAction(@NotNull StringResourceTable table, @NotNull AndroidFacet facet) {
    super("Add Locale", null, AndroidIcons.Globe);

    myTable = table;
    myFacet = facet;
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    long count = ((StringResourceTableModel)myTable.getModel()).getKeys().stream()
      .filter(key -> key.getDirectory() != null)
      .count();

    event.getPresentation().setEnabled(count != 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    StringResourceData data = myTable.getData();
    assert data != null;

    List<Locale> locales = LocaleMenuAction.getAllLocales();

    locales.removeAll(data.getLocales());
    locales.sort(Locale.LANGUAGE_NAME_COMPARATOR);

    JList list = new LocaleList(locales);

    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setItemChoosenCallback(() -> createItem((Locale)list.getSelectedValue()))
      .createPopup();

    popup.showUnderneathOf(event.getInputEvent().getComponent());
  }

  private void createItem(@NotNull Locale locale) {
    StringResource resource = findResource();
    StringResourceKey key = resource.getKey();

    VirtualFile directory = key.getDirectory();
    assert directory != null;

    StringsWriteUtils.createItem(myFacet, directory, locale, key.getName(), resource.getDefaultValueAsString(), true);
  }

  @NotNull
  private StringResource findResource() {
    StringResourceData data = myTable.getData();
    assert data != null;

    StringResourceKey key = new StringResourceKey("app_name", myFacet.getAllResourceDirectories().get(0));

    if (data.containsKey(key)) {
      return data.getStringResource(key);
    }

    Optional<StringResource> optionalResource = data.getResources().stream()
      .filter(resource -> resource.getKey().getDirectory() != null)
      .findFirst();

    return optionalResource.orElseThrow(IllegalStateException::new);
  }
}
