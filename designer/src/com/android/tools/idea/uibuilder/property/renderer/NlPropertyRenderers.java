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

import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellRenderer;
import java.util.Set;

public class NlPropertyRenderers {
  private static NlDefaultRenderer ourDefaultRenderer;
  private static NlBooleanRenderer ourBooleanRenderer;
  private static NlFlagRenderer ourFlagRenderer;
  private static NlFlagItemRenderer ourFlagItemRenderer;

  @NotNull
  public static TableCellRenderer get(@NotNull NlProperty p) {
    AttributeDefinition definition = p.getDefinition();
    if (definition == null) {
      return getDefaultRenderer();
    }

    Set<AttributeFormat> formats = definition.getFormats();
    if (formats.size() == 1 && formats.contains(AttributeFormat.Boolean)) {
      NlBooleanRenderer renderer = getBooleanRenderer();
      if (renderer.canRender(p, formats)) {
        return renderer;
      }
    }
    if (formats.contains(AttributeFormat.Flag)) {
      NlFlagRenderer renderer = getFlagRenderer();
      if (renderer.canRender(p, formats)) {
        return renderer;
      }
    }

    return getDefaultRenderer();
  }

  @NotNull
  public static NlFlagRenderer getFlagRenderer() {
    if (ourFlagRenderer == null) {
      ourFlagRenderer = new NlFlagRenderer();
    }
    return ourFlagRenderer;
  }

  @NotNull
  public static NlFlagItemRenderer getFlagItemRenderer() {
    if (ourFlagItemRenderer == null) {
      ourFlagItemRenderer = new NlFlagItemRenderer();
    }
    return ourFlagItemRenderer;
  }

  @NotNull
  private static NlBooleanRenderer getBooleanRenderer() {
    if (ourBooleanRenderer == null) {
      ourBooleanRenderer = new NlBooleanRenderer();
    }
    return ourBooleanRenderer;
  }

  @NotNull
  private static NlDefaultRenderer getDefaultRenderer() {
    if (ourDefaultRenderer == null) {
      ourDefaultRenderer = new NlDefaultRenderer();
    }
    return ourDefaultRenderer;
  }
}
