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

import com.android.ide.common.resources.Locale;
import com.android.tools.idea.rendering.FlagManager;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBList;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

final class LocaleList extends JBList<Locale> {
  LocaleList(@NotNull Collection<Locale> locales) {
    super(locales);

    setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setIcon(FlagManager.getFlagImage(value));
      label.setText(Locale.getLocaleLabel(value, false));
    }));
    setFixedCellHeight(20);
    setName("localeList");

    ListSpeedSearch.installOn(this, object -> Locale.getLocaleLabel(object, false));
  }
}
