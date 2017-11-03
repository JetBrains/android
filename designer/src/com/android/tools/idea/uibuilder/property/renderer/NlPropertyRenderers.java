/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.adtui.ptable.*;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItemValue;
import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellRenderer;
import java.util.Set;

public class NlPropertyRenderers implements PTableCellRendererProvider {
  private static NlPropertyRenderers ourInstance = new NlPropertyRenderers();

  private final NlTableNameRenderer myTableNameRenderer;
  private final NlBooleanRenderer myBooleanRenderer;
  private final NlFlagRenderer myFlagRenderer;
  private final NlFlagItemRenderer myFlagItemRenderer;
  private final NlDefaultRenderer myDefaultRenderer;
  private final TableCellRenderer myGroupRenderer;

  public static NlPropertyRenderers getInstance() {
    if (ourInstance == null) {
      ourInstance = new NlPropertyRenderers();
    }
    return ourInstance;
  }

  private NlPropertyRenderers() {
    myTableNameRenderer = new NlTableNameRenderer();
    myBooleanRenderer = new NlBooleanRenderer();
    myFlagRenderer = new NlFlagRenderer();
    myFlagItemRenderer = new NlFlagItemRenderer();
    myDefaultRenderer = new NlDefaultRenderer();
    myGroupRenderer = createGroupTableCellRenderer();
  }

  @NotNull
  @Override
  public PNameRenderer getNameCellRenderer(@NotNull PTableItem item) {
    return myTableNameRenderer;
  }

  @NotNull
  @Override
  public TableCellRenderer getValueCellRenderer(@NotNull PTableItem item) {
    if (item instanceof PTableGroupItem) {
      return myGroupRenderer;
    }
    if (item instanceof NlProperty) {
      return get((NlProperty)item);
    }
    throw new IllegalArgumentException("Unrecognized table item " + item);
  }

  @NotNull
  public NlAttributeRenderer get(@NotNull NlProperty property) {
    if (property instanceof NlFlagPropertyItemValue) {
      return myFlagItemRenderer;
    }
    AttributeDefinition definition = property.getDefinition();
    if (definition == null) {
      return myDefaultRenderer;
    }

    Set<AttributeFormat> formats = definition.getFormats();
    if (formats.size() == 1 && formats.contains(AttributeFormat.Boolean)) {
      if (myBooleanRenderer.canRender(property, formats)) {
        return myBooleanRenderer;
      }
    }
    if (formats.contains(AttributeFormat.Flag)) {
      if (myFlagRenderer.canRender(property, formats)) {
        return myFlagRenderer;
      }
    }
    return myDefaultRenderer;
  }

  private static TableCellRenderer createGroupTableCellRenderer() {
    return new PTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem value,
                                           boolean selected, boolean hasFocus, int row, int column) {
      }
    };
  }
}
