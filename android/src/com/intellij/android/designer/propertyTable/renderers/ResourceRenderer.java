/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.propertyTable.renderers;

import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.android.designer.model.RadModelBuilder;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.renderers.AbstractResourceRenderer;
import com.intellij.designer.propertyTable.renderers.BooleanRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class ResourceRenderer extends AbstractResourceRenderer<String> {
  public static final String[] DIMENSIONS = {"dp", "sp", "pt", "px", "mm", "in"};
  private static final String ANDROID_PREFIX = "@android:";

  private BooleanRenderer myBooleanRenderer;
  private final Set<AttributeFormat> myFormats;

  public ResourceRenderer(Set<AttributeFormat> formats) {
    if (formats.contains(AttributeFormat.Boolean)) {
      myBooleanRenderer = new BooleanRenderer();
    }
    myFormats = formats;
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable PropertiesContainer container,
                                 PropertyContext context,
                                 @Nullable Object object,
                                 boolean selected,
                                 boolean hasFocus) {
    String value = (String)object;
    if (myBooleanRenderer != null && (StringUtil.isEmpty(value) || "false".equals(value) || "true".equals(value))) {
      return myBooleanRenderer.getComponent(container, context, "true".equals(value), selected, hasFocus);
    }

    return super.getComponent(container, context, object, selected, hasFocus);
  }

  @Override
  protected void formatValue(RadComponent component, String value) {
    if (!StringUtil.isEmpty(value)) {
      StringBuilder colorValue = new StringBuilder();
      boolean system = false;
      int prefix = -1;
      if (value.startsWith("#")) {
        prefix = 1;
      }
      else if (value.startsWith(ANDROID_PREFIX)) {
        prefix = ANDROID_PREFIX.length();
        system = true;
      }
      else if (value.startsWith("@")) {
        prefix = 1;
      }
      if (prefix != -1) {
        myColoredComponent.append(value.substring(0, prefix), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        myColoredComponent.append(value.substring(prefix), textStyle(component, value, system, colorValue));
      }
      else if (myFormats.contains(AttributeFormat.Dimension)) {
        if (value.length() > 3 && value.endsWith("dip")) {
          myColoredComponent.append(value.substring(0, value.length() - 3));
          myColoredComponent.append("dip", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else if (value.length() > 2) {
          int index = value.length() - 2;
          String dimension = value.substring(index);
          if (ArrayUtil.indexOf(DIMENSIONS, dimension) != -1) {
            myColoredComponent.append(value.substring(0, index));
            myColoredComponent.append(dimension, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          else {
            myColoredComponent.append(value);
          }
        }
        else {
          myColoredComponent.append(value);
        }
      }
      else {
        myColoredComponent.append(value);
      }
      if (colorValue.length() > 0) {
        value = colorValue.toString();
      }
      if (myFormats.contains(AttributeFormat.Color) && value.startsWith("#")) {
        try {
          Color color = ResourceHelper.parseColor(value);
          if (color != null) {
            myColorIcon.setColor(color);
            myColoredComponent.setIcon(myColorIcon);
          }
        }
        catch (Throwable e) {
        }
      }
    }
  }

  private static SimpleTextAttributes textStyle(RadComponent component, String value, boolean system, StringBuilder colorValue) {
    if (value.startsWith("@") && !value.startsWith("@id/") && !value.startsWith("@+id/") && !value.startsWith("@android:id/")) {
      try {
        int start = system ? ANDROID_PREFIX.length() : 1;
        int index = value.indexOf('/', start + 1);
        String type = value.substring(start, index);
        String name = value.substring(index + 1);

        Module module = RadModelBuilder.getModule(component);
        if (module != null) {
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null) {
            ResourceManager manager = facet.getResourceManager(system ? AndroidUtils.SYSTEM_RESOURCE_PACKAGE : null);
            if (manager != null) {
              List<ResourceElement> resources = manager.findValueResources(type, name, false);

              if ("color".equalsIgnoreCase(type) && !resources.isEmpty()) {
                colorValue.append(resources.get(0).getRawText());
              }

              if (resources.isEmpty() && manager.findResourceFiles(type, name, false).isEmpty()) {
                return SimpleTextAttributes.ERROR_ATTRIBUTES;
              }
            }
          }
        }
      }
      catch (Throwable e) {
      }
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  @Override
  public void updateUI() {
    if (myBooleanRenderer != null) {
      SwingUtilities.updateComponentTreeUI(myBooleanRenderer);
    }
    super.updateUI();
  }
}