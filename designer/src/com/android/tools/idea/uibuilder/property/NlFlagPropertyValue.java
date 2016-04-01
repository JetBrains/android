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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.renderer.NlPropertyRenderers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;

public class NlFlagPropertyValue extends PTableItem {
  private final String myName;
  private final NlFlagProperty myFlags;

  public NlFlagPropertyValue(@NotNull String name, @NotNull NlFlagProperty flags) {
    myName = name;
    myFlags = flags;
    setParent(flags);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getValue() {
    return myFlags.isItemSet(this) ? SdkConstants.VALUE_TRUE : SdkConstants.VALUE_FALSE;
  }

  @Override
  public void setValue(@Nullable Object value) {
    if (value == null) {
      value = SdkConstants.VALUE_FALSE;
    }
    myFlags.setItem(this, SdkConstants.VALUE_TRUE.equalsIgnoreCase(value.toString()));
  }

  @NotNull
  @Override
  public TableCellRenderer getCellRenderer() {
    return NlPropertyRenderers.getFlagItemRenderer();
  }

  @Override
  public boolean isEditable(int col) {
    return true;
  }

  @Override
  public PTableCellEditor getCellEditor() {
    return NlPropertyEditors.getFlagEditor();
  }
}
