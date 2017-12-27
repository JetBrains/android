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

import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

public class TextRowFilter extends StringResourceTableRowFilter {

  @NotNull private final String myText;

  public TextRowFilter(@NotNull String text) {
    myText = text;
  }

  @Override
  public boolean include(Entry<? extends StringResourceTableModel, ? extends Integer> entry) {
    for (int i = 0; i < entry.getValueCount(); i++) {
      String text = entry.getStringValue(i);
      if (text.contains(myText)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void update(@NotNull Presentation presentation) {
    presentation.setIcon(null);
    presentation.setText("Show Keys with Values Containing \"" + myText + '"');
  }
}
