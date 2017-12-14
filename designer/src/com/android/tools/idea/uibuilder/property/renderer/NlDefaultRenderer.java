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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.GutterIconCache;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.AndroidColorAnnotator;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Set;

public class NlDefaultRenderer extends NlAttributeRenderer {
  public static final int ICON_SIZE = 14;

  @Override
  protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem value,
                                       boolean selected, boolean hasFocus, int row, int col) {
    if (value instanceof NlProperty) {
      customize((NlProperty)value, col, selected);
    }
  }

  @VisibleForTesting
  void customize(NlProperty property, int column, boolean selected) {
    if (column == 0) {
      appendName(property);
    } else {
      appendValue(property, selected);
    }
  }

  private void appendValue(@NotNull NlProperty property, boolean selected) {
    String value = property.getValue();
    String text = StringUtil.notNullize(value);
    Icon icon = getIcon(property, ICON_SIZE);
    if (icon != null) {
      setIcon(icon);
    }
    if (!selected && !property.isDefaultValue(value)) {
      setForeground(JBColor.BLUE);
    }
    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    setToolTipText(text);
  }

  @Nullable
  public static Icon getIcon(@NotNull NlProperty property, int iconSize) {
    String text = property.getResolvedValue();
    if (text == null) {
      return null;
    }

    if (isColorValue(text)) {
      return getColorIcon(text, iconSize);
    }

    Configuration configuration = property.getModel().getConfiguration();
    //noinspection ConstantConditions
    if (configuration == null) { // happens in unit test
      return null;
    }

    ResourceResolver resolver = configuration.getResourceResolver();
    if (resolver == null) {
      return null;
    }

    if (text.startsWith(SdkConstants.COLOR_RESOURCE_PREFIX)
      || text.startsWith(SdkConstants.ANDROID_COLOR_RESOURCE_PREFIX)) {
      return getColorIcon(resolver, property, text, iconSize);
    }

    if (text.startsWith(SdkConstants.DRAWABLE_PREFIX) ||
        text.startsWith(SdkConstants.ANDROID_DRAWABLE_PREFIX) ||
        text.startsWith(SdkConstants.MIPMAP_PREFIX)) {
      return getDrawableIcon(resolver, property, text);
    }

    return null;
  }

  @Nullable
  private static Icon getDrawableIcon(@NotNull ResourceResolver resolver, @NotNull NlProperty property, @NotNull String value) {
    ResourceType type = value.startsWith(SdkConstants.MIPMAP_PREFIX) ? ResourceType.MIPMAP : ResourceType.DRAWABLE;
    ResourceValue drawable = resolver.resolveResValue(new ResourceValue(new ResourceReference(type, property.getName(), false), value));
    if (drawable == null) {
      return null;
    }

    File file = AndroidColorAnnotator.pickBestBitmap(ResourceHelper.resolveDrawable(resolver, drawable, property.getModel().getProject()));
    return file == null ? null : GutterIconCache.getInstance().getIcon(file.getPath(), resolver);
  }

  @Nullable
  private static Icon getColorIcon(@NotNull ResourceResolver resolver, @NotNull NlProperty property, @NotNull String value, int iconSize) {
    ResourceValue resourceValue = resolver.resolveResValue(new ResourceValue(new ResourceReference(ResourceType.COLOR,
                                                                                                   property.getName(),
                                                                                                   false),
                                                                             value));
    if (resourceValue == null) {
      return null;
    }

    String resolvedValue = resourceValue.getValue();
    if (isColorValue(resolvedValue)) {
      return getColorIcon(resolvedValue, iconSize);
    }

    return null;
  }

  private static boolean isColorValue(@Nullable String value) {
    return value != null && value.startsWith("#") && value.matches("#\\p{XDigit}+");
  }

  @Nullable
  private static Icon getColorIcon(@NotNull String hexColor, int iconSize) {
    Color color = ResourceHelper.parseColor(hexColor);
    return color == null ? null : JBUI.scale(new ColorIcon(iconSize, color, true));
  }

  private void appendName(@NotNull NlProperty property) {
    append(property.getName());
    setToolTipText(property.getTooltipText());
  }

  @Override
  public boolean canRender(@NotNull NlProperty p, @NotNull Set<AttributeFormat> formats) {
    return true;
  }

  @VisibleForTesting
  SimpleColoredComponent getLabel() {
    return this;
  }
}
