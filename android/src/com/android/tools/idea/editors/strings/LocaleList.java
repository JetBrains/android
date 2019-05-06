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
import com.android.tools.idea.rendering.Locale;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

final class LocaleList extends JBList {
  LocaleList(@NotNull Collection<Locale> locales) {
    super(locales);

    setCellRenderer(new ColoredListCellRenderer<Locale>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends Locale> list,
                                           @NotNull Locale locale,
                                           int index,
                                           boolean selected,
                                           boolean focused) {
        setIcon(locale.getFlagImage());
        append(LocaleMenuAction.getLocaleLabel(locale, false));
      }
    });

    setFixedCellHeight(20);
    setName("localeList");

    new ListSpeedSearch(this, (Convertor<Object, String>)object -> LocaleMenuAction.getLocaleLabel((Locale)object, false));
  }
}
