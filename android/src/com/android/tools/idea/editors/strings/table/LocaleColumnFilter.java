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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

public class LocaleColumnFilter implements StringResourceTableColumnFilter {

  @NotNull private final Locale myLocale;

  public LocaleColumnFilter(@NotNull Locale locale) {
    myLocale = locale;
  }

  @Override
  public boolean include(@NotNull Locale locale) {
    return myLocale.equals(locale);
  }

  @Override
  public void update(@NotNull Presentation presentation) {
    presentation.setIcon(myLocale.getFlagImage());
    presentation.setText(LocaleMenuAction.getLocaleLabel(myLocale, false));
  }
}
