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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.GutterIconCache;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.android.AndroidColorAnnotator;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Set;

public class NlDefaultRenderer extends NlAttributeRenderer {
  public static final int ICON_SIZE = 14;
  private final SimpleColoredComponent myLabel;

  public NlDefaultRenderer() {
    JPanel panel = getContentPanel();

    myLabel = new SimpleColoredComponent();
    panel.add(myLabel, BorderLayout.CENTER);
  }

  @Override
  public void customizeRenderContent(@NotNull JTable table, @NotNull NlProperty p, boolean selected, boolean hasFocus, int row, int col) {
    myLabel.clear();
    customize(p, col);
  }

  @Override
  public Icon getHoverIcon(@NotNull NlProperty property) {
    AttributeDefinition definition = property.getDefinition();
    if (NlReferenceEditor.hasResourceChooser(property)) {
      return AllIcons.General.Ellipsis;
    }
    else if (definition != null && definition.getFormats().contains(AttributeFormat.Enum)) {
      return AllIcons.General.ComboArrowDown;
    }
    else {
      return AllIcons.General.EditItemInSection;
    }
  }

  @VisibleForTesting
  void customize(NlProperty property, int column) {
    if (column == 0) {
      appendName(property);
    } else {
      appendValue(property);
    }
  }

  private void appendValue(@NotNull NlProperty property) {
    String value = property.getValue();
    String text = StringUtil.notNullize(value);
    Icon icon = getIcon(property);
    if (icon != null) {
      myLabel.setIcon(icon);
    }
    if (!property.isDefaultValue(value)) {
      myLabel.setForeground(JBColor.BLUE);
    }

    myLabel.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myLabel.setToolTipText(text);
  }

  @Nullable
  public static Icon getIcon(@NotNull NlProperty property) {
    Object value = property.getValue();
    if (value == null) {
      return null;
    }
    String text = property.resolveValue(value.toString());
    if (text == null) {
      return null;
    }

    if (isColorValue(text)) {
      return getColorIcon(text);
    }

    Configuration configuration = property.getComponent().getModel().getConfiguration();
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
      return getColorIcon(resolver, property, text);
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
    boolean isFrameworkRes = value.startsWith(SdkConstants.ANDROID_PREFIX);
    ResourceType type = value.startsWith(SdkConstants.MIPMAP_PREFIX) ? ResourceType.MIPMAP : ResourceType.DRAWABLE;
    ResourceValue drawable = resolver.resolveValue(type, property.getName(), value, isFrameworkRes);
    if (drawable == null) {
      return null;
    }

    File file = AndroidColorAnnotator.pickBestBitmap(ResourceHelper.resolveDrawable(resolver, drawable, property.getComponent().getModel().getProject()));
    return file == null ? null : GutterIconCache.getInstance().getIcon(file.getPath());
  }

  @Nullable
  private static Icon getColorIcon(@NotNull ResourceResolver resolver, @NotNull NlProperty property, @NotNull String value) {
    boolean isFrameworkRes = value.startsWith(SdkConstants.ANDROID_COLOR_RESOURCE_PREFIX);
    ResourceValue resourceValue = resolver.resolveValue(ResourceType.COLOR, property.getName(), value, isFrameworkRes);
    if (resourceValue == null) {
      return null;
    }

    String resolvedValue = resourceValue.getValue();
    if (isColorValue(resolvedValue)) {
      return getColorIcon(resolvedValue);
    }

    return null;
  }

  private static boolean isColorValue(@Nullable String value) {
    return value != null && value.startsWith("#") && value.matches("#\\p{XDigit}+");
  }

  @Nullable
  private static Icon getColorIcon(@NotNull String hexColor) {
    Color color = ResourceHelper.parseColor(hexColor);
    return color == null ? null : new ColorIcon(ICON_SIZE, color, true);
  }

  private void appendName(@NotNull NlProperty property) {
    myLabel.append(property.getName());
    myLabel.setToolTipText(property.getTooltipText());
  }

  @Override
  public boolean canRender(@NotNull NlProperty p, @NotNull Set<AttributeFormat> formats) {
    return true;
  }

  @VisibleForTesting
  SimpleColoredComponent getLabel() {
    return myLabel;
  }
}
