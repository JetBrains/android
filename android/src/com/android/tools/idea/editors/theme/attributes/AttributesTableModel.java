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
package com.android.tools.idea.editors.theme.attributes;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.annotations.NotNull;
import spantable.CellSpanModel;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Table model for Theme Editor
 */
public class AttributesTableModel extends AbstractTableModel implements CellSpanModel {
  private final static Logger LOG = Logger.getInstance(AttributesTableModel.class);

  // Cells containing values with classes in WIDE_CLASSES are going to have column span 2
  private static final Set<Class<?>> WIDE_CLASSES = ImmutableSet.of(Color.class, DrawableDomElement.class);

  protected final List<EditedStyleItem> myAttributes;
  private List<TableLabel> myLabels;
  private String myParentName;
  protected final ThemeEditorStyle mySelectedStyle;
  protected final AttributeDefinitions myAttributeDefinitions;

  private final List<ThemePropertyChangedListener> myThemePropertyChangedListeners = new ArrayList<ThemePropertyChangedListener>();

  public interface ThemePropertyChangedListener {
    void attributeChangedOnReadOnlyTheme(final EditedStyleItem attribute, final String newValue);
  }

  public void addThemePropertyChangedListener(final ThemePropertyChangedListener listener) {
    myThemePropertyChangedListeners.add(listener);
  }

  public String getParentName() {
    return myParentName;
  }

  /**
   * Constructor
   *
   * @param selectedStyle current selectedStyle
   */
  public AttributesTableModel(@NotNull ThemeEditorStyle selectedStyle) {
    myAttributes = new ArrayList<EditedStyleItem>();
    myLabels = new ArrayList<TableLabel>();
    mySelectedStyle = selectedStyle;
    myAttributeDefinitions = selectedStyle.getResolver().getAttributeDefinitions();

    ThemeEditorStyle parent = selectedStyle.getParent();
    myParentName = (parent != null) ? parent.getName() : null;
    reloadContent();
  }

  private void reloadContent() {
    final List<EditedStyleItem> rawAttributes = ThemeEditorUtils.resolveAllAttributes(mySelectedStyle);
    myAttributes.clear();
    myLabels = AttributesSorter.generateLabels(rawAttributes, myAttributes);
    fireTableStructureChanged();
  }

  public RowContents getRowContents(final int rowIndex) {
    if (rowIndex == 0) {
      return new ParentAttribute();
    }

    int offset = 1;
    for (final TableLabel label : myLabels) {
      final int labelRowIndex = label.getRowPosition() + offset;

      if (labelRowIndex < rowIndex) {
        offset++;
      }
      else if (labelRowIndex == rowIndex) {
        return new LabelContents(label);
      }
      else { // labelRowIndex > rowIndex
        return new AttributeContents(rowIndex - offset);
      }
    }

    return new AttributeContents(rowIndex - offset);
  }

  @Override
  public int getRowCount() {
    return myAttributes.size() + myLabels.size() + 1;
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return getRowContents(rowIndex).getValueAt(columnIndex);
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    getRowContents(rowIndex).setValueAt(columnIndex, aValue);
  }

  @Override
  public int getColumnSpan(int row, int column) {
    return getRowContents(row).getColumnSpan(column);
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return getRowContents(rowIndex).isCellEditable(columnIndex);
  }

  @Override
  public int getRowSpan(int row, int column) {
    return 1;
  }

  @Override
  public Class<?> getCellClass(int row, int column) {
    return getRowContents(row).getCellClass(column);
  }

  protected boolean isReadOnly() {
    return false;
  }

  /**
   * Basically a union type, RowContents = LabelContents | AttributeContents | ParentAttribute
   */
  private interface RowContents {
    int getColumnSpan(int column);

    Object getValueAt(int column);

    void setValueAt(int column, Object value);

    Class<?> getCellClass(int column);

    boolean isCellEditable(int column);
  }

  private class ParentAttribute implements RowContents {
    @Override
    public int getColumnSpan(int column) {
      return 1;
    }

    @Override
    public Object getValueAt(int column) {
      if (column == 0) {
        return "Theme Parent";
      }
      else {
        return myParentName == null ? "[no parent]" : myParentName;
      }
    }

    @Override
    public void setValueAt(int column, Object value) {
      if (column == 0) {
        throw new RuntimeException("Tried to setValue at parent attribute label");
      }
      else {
        myParentName = (String)value;
        //Changes the value of Parent in XML
        mySelectedStyle.setParent(myParentName);
        reloadContent();
      }
    }

    @Override
    public Class<?> getCellClass(int column) {
      return String.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      return (column == 1 && !mySelectedStyle.isReadOnly());
    }
  }

  private class LabelContents implements RowContents {
    private final TableLabel myLabel;

    private LabelContents(TableLabel label) {
      myLabel = label;
    }

    @Override
    public int getColumnSpan(int column) {
      return column == 0 ? getColumnCount() : 0;
    }

    @Override
    public Object getValueAt(int column) {
      return myLabel;
    }

    @Override
    public Class<?> getCellClass(int column) {
      return TableLabel.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      return false;
    }

    @Override
    public void setValueAt(int column, Object value) {
      throw new RuntimeException(String.format("Tried to setValue at immutable label row of LabelledModel, column = %1$d" + column));
    }
  }

  private class AttributeContents implements RowContents {
    private final int myRowIndex;

    public AttributeContents(int rowIndex) {
      myRowIndex = rowIndex;
    }

    @Override
    public int getColumnSpan(int column) {
      if (WIDE_CLASSES.contains(getCellClass(column))) {
        return column == 0 ? getColumnCount() : 0;
      }
      return 1;
    }

    @Override
    public Object getValueAt(int column) {
      EditedStyleItem attribute = myAttributes.get(myRowIndex);
      Class cellClass = getCellClass(column);

      // TODO: can we work out the type by looking at the value?
      if (WIDE_CLASSES.contains(cellClass)) {
        return attribute;
      }

      String propertyName = attribute.getQualifiedName();

      if (column == 0) {
        return propertyName;
      }

      return attribute;
    }

    @Override
    public Class<?> getCellClass(int column) {
      EditedStyleItem item = myAttributes.get(myRowIndex);
      String name = item.getName();
      AttributeDefinition attrDefinition = myAttributeDefinitions.getAttrDefByName(name);

      ResourceValue resourceValue = mySelectedStyle.getConfiguration().getResourceResolver().resolveResValue(item.getItemResourceValue());
      if (resourceValue == null) {
        LOG.error("Unable to resolve " + item.getValue());
        return null;
      }

      ResourceType urlType = resourceValue.getResourceType();
      String value = resourceValue.getValue();

      if (urlType == ResourceType.DRAWABLE) {
        return DrawableDomElement.class;
      }

      if (urlType == ResourceType.COLOR
              || (value != null && value.startsWith("#") && ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Color))) {
        return Color.class;
      }

      if (column == 1) {
        if (urlType == ResourceType.STYLE) {
          return ThemeEditorStyle.class;
        }
        if (urlType == ResourceType.INTEGER || ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Integer)) {
          return Integer.class;
        }
        if (urlType == ResourceType.BOOL
                || (("true".equals(value) || "false".equals(value))
                        && ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Boolean))) {
          return Boolean.class;
        }
      }

      return String.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      if (isReadOnly()) {
      /*
       * Ideally we should allow framework themes to be modified and then ask the user where to put the new value. We currently simplify
       * this flow by only allowing the user to modify project themes.
       */
        return false;
      }

      // Color rows are editable. Also the middle column for all other attributes.
      return WIDE_CLASSES.contains(getCellClass(column)) || column == 1;
    }

    @Override
    public void setValueAt(int column, Object value) {
      if (value == null) {
        return;
      }

      if (mySelectedStyle.isReadOnly()) {
        for (ThemePropertyChangedListener listener : myThemePropertyChangedListeners) {
          listener.attributeChangedOnReadOnlyTheme((EditedStyleItem)getValueAt(1), value.toString());
        }
        return;
      }

      String strValue = value.toString();
      EditedStyleItem rv = myAttributes.get(myRowIndex);
      if (strValue.equals(rv.getRawXmlValue())) {
        return;
      }
      String propertyName = rv.getQualifiedName();
      rv.setValue(strValue);
      mySelectedStyle.setValue(propertyName, strValue);
      fireTableCellUpdated(myRowIndex, column);
    }
  }
}
