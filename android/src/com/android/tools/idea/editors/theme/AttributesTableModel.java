/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ItemResourceValue;
import spantable.CellSpanModel;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link javax.swing.table.TableModel} for style attributes. It implements {@link spantable.CellSpanModel} so rows
 * can span over more than one column for things like color attributes.
 */
class AttributesTableModel extends AbstractTableModel implements CellSpanModel {
  private static final Logger LOG = Logger.getInstance(AttributesTableModel.class);

  private static final Pattern ANDROID_PREFIX_START = Pattern.compile("^android:");
  // When enabled, we display a third column with the resource type.


  protected final AttributeDefinitions myAttributeDefinitions;
  protected final List<EditedStyleItem> myAttributes;
  protected final ThemeEditorStyle mySelectedStyle;

  AttributesTableModel(@NotNull ThemeEditorStyle selectedStyle, @NotNull List<EditedStyleItem> attributes) {
    mySelectedStyle = selectedStyle;
    myAttributeDefinitions = selectedStyle.getResolver().getAttributeDefinitions();
    myAttributes = attributes;
  }

  /**
   * Returns whether a certain attribute accepts a given attribute format.
   */
  private static boolean acceptsFormat(@Nullable AttributeDefinition attrDefByName, @NotNull AttributeFormat want) {
    if (attrDefByName == null) {
      return false;
    }

    return attrDefByName.getFormats().contains(want);
  }

  public static Object extractRealValue(final ItemResourceValue value, final Class<?> desiredClass) {
    if (desiredClass == Boolean.class) {
      return Boolean.valueOf(value.getValue());
    }
    else if (desiredClass == Integer.class) {
      return Integer.parseInt(value.getValue());
    }
    else {
      return value.getRawXmlValue();
    }
  }

  /**
   * Returns whether the selected theme is read-only.
   */
  protected boolean isReadOnly() {
    return mySelectedStyle.isReadOnly();
  }

  @Override
  public int getColumnSpan(int row, int column) {
    if (getCellClass(row, column) == Color.class) {
      return column == 0 ? getColumnCount() : 0;
    }
    return 1;
  }

  @Override
  public int getRowSpan(int row, int column) {
    return 1;
  }

  @Override
  public Class<?> getCellClass(int row, int column) {
    EditedStyleItem item = myAttributes.get(row);
    String name = item.getName();
    AttributeDefinition attrDefinition = myAttributeDefinitions.getAttrDefByName(name);

    if (acceptsFormat(attrDefinition, AttributeFormat.Color)) {
      return Color.class;
    }

    if (column == 1) {
      // TODO: We temporarily don't support attr values as part of the sub-style editing.
      // We can drill-down in styles, and theme properties to edit them.
      if ((name.contains("Style") || name.endsWith("Theme")) &&
          (attrDefinition == null || acceptsFormat(attrDefinition, AttributeFormat.Reference)) &&
          item.isValueReference()) {
        return ThemeEditorStyle.class;
      }

      if (acceptsFormat(attrDefinition, AttributeFormat.Integer)) {
        return Integer.class;
      }
      else if (acceptsFormat(attrDefinition, AttributeFormat.Boolean)) {
        return Boolean.class;
      }
    }
    return String.class;
  }

  @Override
  public int getRowCount() {
    return myAttributes.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public boolean isCellEditable(final int rowIndex, final int columnIndex) {
    if (isReadOnly()) {
      /*
       * Ideally we should allow framework themes to be modified and then ask the user where to put the new value. We currently simplify
       * this flow by only allowing the user to modify project themes.
       */
      return false;
    }

    // Color rows are editable. Also the middle column for all other attributes.
    return getCellClass(rowIndex, columnIndex) == Color.class || columnIndex == 1;
  }

  @Nullable
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    EditedStyleItem attribute = myAttributes.get(rowIndex);
    Class cellClass = getCellClass(rowIndex, columnIndex);

    // TODO: can we work out the type by looking at the value?
    if (cellClass == Color.class) {
      return attribute;
    }

    String propertyName = attribute.getName();

    if (columnIndex == 0) {
      return propertyName;
    }

    return attribute;
  }

  @Override
  public void setValueAt(@Nullable Object aValue, int rowIndex, int columnIndex) {
    if (isReadOnly()) {
      LOG.error("This theme can not be modified");
      return;
    }

    if (aValue == null) {
      return;
    }
    String strValue = aValue.toString();
    EditedStyleItem rv = myAttributes.get(rowIndex);
    if (strValue.equals(rv.getRawXmlValue())) {
      return;
    }

    // If the values is a theme attribute, we might end up modifying a different row.
    int updatedRowIndex = rowIndex;

    String propertyName;
    if (rv.isAttr()) {
      // For theme attributes, we need to set the value of the attr and not the property we are adding.
      // TODO: Maybe we should just link to the attribute that the attr links to?
      propertyName = rv.getAttrPropertyName();

      if (myAttributeDefinitions.getAttrDefByName(propertyName.substring(propertyName.indexOf(":") + 1)) == null) {
        LOG.error(propertyName + " attribute does not exist.");
      }

      for (int i = 0; i < myAttributes.size(); i++) {
        EditedStyleItem attribute = myAttributes.get(i);
        if (attribute.getQualifiedName().equals(propertyName)) {
          updatedRowIndex = i;
          attribute.setValue(strValue);
          break;
        }
      }
    }
    else {
      propertyName = rv.getQualifiedName();
      rv.setValue(strValue);
    }

    mySelectedStyle.setValue(propertyName, strValue);
    fireTableCellUpdated(updatedRowIndex, columnIndex);
  }
}
