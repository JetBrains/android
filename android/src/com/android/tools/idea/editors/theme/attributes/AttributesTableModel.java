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
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.ThemeAttributeResolver;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.android.dom.resources.Flag;
import org.jetbrains.annotations.NotNull;
import spantable.CellSpanModel;

import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Table model for Theme Editor
 */
public class AttributesTableModel extends AbstractTableModel implements CellSpanModel {
  private static final Logger LOG = Logger.getInstance(AttributesTableModel.class);
  public static final int COL_COUNT = 2;
  /** Cells containing values with classes in WIDE_CLASSES are going to have column span 2 */
  private static final Set<Class<?>> WIDE_CLASSES = ImmutableSet.of(Color.class, DrawableDomElement.class);

  protected final List<EditedStyleItem> myAttributes;
  private List<TableLabel> myLabels;

  protected final ConfiguredThemeEditorStyle mySelectedStyle;

  private final AttributesGrouper.GroupBy myGroupBy;
  private final ThemeEditorContext myContext;

  private final List<ThemePropertyChangedListener> myThemePropertyChangedListeners = new ArrayList<ThemePropertyChangedListener>();
  private final ParentAttribute myParentAttribute = new ParentAttribute();

  public interface ThemePropertyChangedListener {
    void attributeChangedOnReadOnlyTheme(final EditedStyleItem attribute, final String newValue);
  }

  public void addThemePropertyChangedListener(final ThemePropertyChangedListener listener) {
    myThemePropertyChangedListeners.add(listener);
  }

  public ImmutableSet<String> getDefinedAttributes() {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();

    for (EditedStyleItem item : myAttributes) {
      builder.add(item.getQualifiedName());
    }

    return builder.build();
  }

  public AttributesTableModel(@NotNull ConfiguredThemeEditorStyle selectedStyle,
                              @NotNull AttributesGrouper.GroupBy groupBy,
                              @NotNull ThemeEditorContext context) {
    myContext = context;
    myAttributes = new ArrayList<EditedStyleItem>();
    myLabels = new ArrayList<TableLabel>();
    mySelectedStyle = selectedStyle;
    myGroupBy = groupBy;
    reloadContent();
  }

  private void reloadContent() {
    final List<EditedStyleItem> rawAttributes = ThemeAttributeResolver.resolveAll(mySelectedStyle, myContext.getConfiguration().getConfigurationManager());

    //noinspection unchecked (SIMPLE_MODE_COMPARATOR can compare EditedStyleItem)
    Collections.sort(rawAttributes, ThemeEditorComponent.SIMPLE_MODE_COMPARATOR);
    myAttributes.clear();
    myLabels = AttributesGrouper.generateLabels(myGroupBy, rawAttributes, myAttributes);
    fireTableStructureChanged();
  }

  @NotNull
  public RowContents getRowContents(final int rowIndex) {
    if (rowIndex == 0) {
      return myParentAttribute;
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

  /**
   * returns true if this row is not a attribute or a label and is the Theme parent.
   */
  public boolean isThemeParentRow(int row) {
    return row == 0;
  }

  @Override
  public int getRowCount() {
    return myAttributes.size() + myLabels.size() + 1;
  }

  @Override
  public int getColumnCount() {
    return COL_COUNT;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return getRowContents(rowIndex).getValue();
  }

  /**
   * Implementation of setValueAt method of TableModel interface. Parameter aValue has type Object because of
   * interface definition, but all passed values are expected to have type AttributeEditorValue.
   * @param aValue value to be set at a cell with given row an column index, should have type AttributeEditorValue
   */
  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    getRowContents(rowIndex).setValueAt(columnIndex, (String) aValue);
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

  @NotNull
  public ConfiguredThemeEditorStyle getSelectedStyle() {
    return mySelectedStyle;
  }

  /**
   * Basically a union type, RowContents = LabelContents | AttributeContents | ParentAttribute
   *
   * @param <T> type of a value stored in a row
   */
  public interface RowContents<T> {
    int getColumnSpan(int column);

    T getValue();

    void setValueAt(int column, String value);

    Class<?> getCellClass(int column);

    boolean isCellEditable(int column);
  }

  public class ParentAttribute implements RowContents<Object> {

    @Override
    public int getColumnSpan(int column) {
      return column == 0 ? getColumnCount() : 0;
    }

    @Override
    public Object getValue() {
       return mySelectedStyle;
    }

    @Override
    public void setValueAt(int column, String newName) {
      ConfiguredThemeEditorStyle parent = mySelectedStyle.getParent();
      if (parent == null || !parent.getQualifiedName().equals(newName)) {
        // Changes the value of parent in XML
        mySelectedStyle.setParent(newName);
        fireTableCellUpdated(0, 0);
      }
    }

    @Override
    public Class<?> getCellClass(int column) {
      return ParentAttribute.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      return !mySelectedStyle.isReadOnly();
    }
  }

  public class LabelContents implements RowContents<TableLabel> {
    private final TableLabel myLabel;

    private LabelContents(TableLabel label) {
      myLabel = label;
    }

    @Override
    public int getColumnSpan(int column) {
      return column == 0 ? getColumnCount() : 0;
    }

    @Override
    public TableLabel getValue() {
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
    public void setValueAt(int column, String value) {
      throw new RuntimeException(String.format("Tried to setValue at immutable label row of LabelledModel, column = %1$d", column));
    }
  }

  public class AttributeContents implements RowContents<EditedStyleItem> {
    private final int myRowIndex;

    public AttributeContents(int rowIndex) {
      myRowIndex = rowIndex;
    }

    public int getRowIndex() {
      return myRowIndex;
    }

    @Override
    public int getColumnSpan(int column) {
      if (WIDE_CLASSES.contains(getCellClass(column))) {
        return column == 0 ? getColumnCount() : 0;
      }
      return 1;
    }

    @Override
    public EditedStyleItem getValue() {
      return myAttributes.get(myRowIndex);
    }

    @Override
    public Class<?> getCellClass(int column) {
      // This could be called with a disposed module
      // (e.g. theme editor table reloading while the project is closing)
      // In that case, getting the resource resolver would fail
      if (myContext.getCurrentContextModule().isDisposed()) {
        return null;
      }

      ResourceResolver resolver = myContext.getResourceResolver();
      if (resolver == null) {
        // The resolver might be null if the configuration doesn't have a theme selected
        LOG.error("Unable to get resource resolver");
        return null;
      }

      EditedStyleItem item = myAttributes.get(myRowIndex);
      ResourceValue resourceValue = resolver.resolveResValue(item.getSelectedValue());
      if (resourceValue == null) {
        LOG.error("Unable to resolve " + item.getValue());
        return null;
      }

      ResourceType urlType = resourceValue.getResourceType();

      if (urlType == ResourceType.DRAWABLE) {
        return DrawableDomElement.class;
      }

      AttributeDefinition attrDefinition =
        ResolutionUtils.getAttributeDefinition(myContext.getConfiguration(), item.getSelectedValue());

      String attributeName = item.getName().toLowerCase(Locale.US);

      if (urlType == ResourceType.COLOR ||
          ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Color) ||
          attributeName.contains("color")) {
        return Color.class;
      }

      if (ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Reference) &&
          attributeName.contains("background") &&
          !attributeName.contains("style")) {
        return DrawableDomElement.class;
      }

      if (urlType == ResourceType.STYLE) {
        return ConfiguredThemeEditorStyle.class;
      }

      if (ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Flag)) {
        return Flag.class;
      }

      if (ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Enum)) {
        return Enum.class;
      }

      if (urlType == ResourceType.INTEGER || ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Integer)) {
        return Integer.class;
      }

      String value = resourceValue.getValue();
      if (urlType == ResourceType.BOOL
          || (("true".equals(value) || "false".equals(value))
              && ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Boolean))) {
        return Boolean.class;
      }

      return EditedStyleItem.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      EditedStyleItem item = myAttributes.get(myRowIndex);
      // Color rows are editable. Also the middle column for all other attributes.
      return (WIDE_CLASSES.contains(getCellClass(column)) || column == 1) && item.isPublicAttribute();
    }

    @Override
    public void setValueAt(int column, String value) {
      if (value == null) {
        return;
      }

      if (mySelectedStyle.isReadOnly()) {
        for (ThemePropertyChangedListener listener : myThemePropertyChangedListeners) {
          listener.attributeChangedOnReadOnlyTheme(getValue(), value);
        }
        return;
      }

      // Color editing may return reference value, which can be the same as previous value
      // in this cell, but updating table is still required because value that reference points
      // to was changed. To preserve this information, ColorEditor returns ColorEditorValue data
      // structure with value and boolean flag which shows whether reload should be forced.

      if (setAttributeValue(value)) {
        fireTableCellUpdated(myRowIndex, column);
      }
    }

    private boolean setAttributeValue(@NotNull String strValue) {
      EditedStyleItem rv = myAttributes.get(myRowIndex);
      if (strValue.equals(rv.getValue())) {
        return false;
      }
      String propertyName = rv.getQualifiedName();
      mySelectedStyle.setValue(propertyName, strValue);
      return true;
    }
  }
}
