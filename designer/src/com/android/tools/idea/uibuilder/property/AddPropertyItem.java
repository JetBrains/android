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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.ptable.PTableItem;
import com.google.common.collect.Table;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

public class AddPropertyItem extends PTableItem {
  private final Table<String, String, NlPropertyItem> myProperties;
  private String myName;
  private NlProperty myProperty;

  public AddPropertyItem(@NotNull Table<String, String, NlPropertyItem> properties) {
    myName = "";
    myProperty = EmptyProperty.INSTANCE;
    myProperties = properties;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getValue() {
    return myProperty.getValue();
  }

  @NotNull
  public NlProperty getProperty() {
    return myProperty;
  }

  @NotNull
  public List<String> getUnspecifiedProperties() {
    List<String> items = new ArrayList<>();
    for (String namespace : myProperties.rowKeySet()) {
      if (namespace.equals(TOOLS_URI)) {
        continue;
      }
      for (NlPropertyItem property : myProperties.row(namespace).values()) {
        if (property.getValue() == null) {
          addProperty(items, namespace, property.getName());
        }
        NlProperty designProperty = myProperties.get(TOOLS_URI, property.getName());
        if (designProperty == null || designProperty.getValue() == null) {
          addProperty(items, TOOLS_URI, property.getName());
        }
      }
    }
    return items;
  }

  private static void addProperty(@NotNull List<String> items, @NotNull String namespace, @NotNull String name) {
    String prefix = "";
    switch (namespace) {
      case ANDROID_URI:
        prefix = PREFIX_ANDROID;
        break;
      case AUTO_URI:
        prefix = PREFIX_APP;
        break;
      case TOOLS_URI:
        prefix = TOOLS_NS_NAME_PREFIX;
        break;
    }
    items.add(prefix + name);
  }

  @NotNull
  public NlProperty findPropertyByQualifiedName(@NotNull String name) {
    String namespace = "";
    String propertyName = name;
    List<String> names = StringUtil.split(name, ":", false);
    if (names.size() > 1) {
      namespace = convertNamespacePrefix(names.get(0));
      propertyName = names.get(1);
    }
    NlProperty property = myProperties.get(namespace, propertyName);
    if (property == null && namespace.equals(TOOLS_URI)) {
      property = myProperties.get(AUTO_URI, propertyName);
      if (property == null) {
        property = myProperties.get(ANDROID_URI, propertyName);
      }
      return property != null ? property.getDesignTimeProperty() : EmptyProperty.INSTANCE;
    }
    return property != null && property.getValue() == null ? property : EmptyProperty.INSTANCE;
  }

  private static String convertNamespacePrefix(@NotNull String prefix) {
    switch (prefix) {
      case PREFIX_ANDROID:
        return ANDROID_URI;
      case PREFIX_APP:
        return AUTO_URI;
      case TOOLS_NS_NAME_PREFIX:
        return TOOLS_URI;
      default:
        return "";
    }
  }

  @Override
  public boolean isEditable(int column) {
    return column == 0 || isPropertyNameSelected();
  }

  @Override
  public int getColumnToEdit() {
    return isPropertyNameSelected() ? 1 : 0;
  }

  @Override
  public void setValue(@Nullable Object value) {
    if (!isPropertyNameSelected()) {
      myName = value == null ? "" : value.toString().trim();
    }
    else {
      myProperty.setValue(value);
    }
  }

  public void updateProperty() {
    // TODO: Add some kind of error message if no property was found or a duplicate was selected.
    myProperty = findPropertyByQualifiedName(myName);
  }

  public boolean isPropertyNameSelected() {
    return myProperty != EmptyProperty.INSTANCE;
  }
}
