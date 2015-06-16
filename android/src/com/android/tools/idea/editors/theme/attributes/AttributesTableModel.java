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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.StyleResolver;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.android.dom.resources.Flag;
import org.jetbrains.annotations.NotNull;
import spantable.CellSpanModel;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Table model for Theme Editor
 */
public class AttributesTableModel extends AbstractTableModel implements CellSpanModel {
  private static final Logger LOG = Logger.getInstance(AttributesTableModel.class);

  /** Cells containing values with classes in WIDE_CLASSES are going to have column span 2 */
  private static final Set<Class<?>> WIDE_CLASSES = ImmutableSet.of(Color.class, DrawableDomElement.class);

  protected final List<EditedStyleItem> myAttributes;
  private List<TableLabel> myLabels;

  protected final ThemeEditorStyle mySelectedStyle;

  private final AttributesGrouper.GroupBy myGroupBy;
  private final ResourceResolver myResourceResolver;
  private final Project myProject;

  private final List<ThemePropertyChangedListener> myThemePropertyChangedListeners = new ArrayList<ThemePropertyChangedListener>();
  public final ParentAttribute parentAttribute = new ParentAttribute();

  private ThemeEditorComponent.GoToListener myGoToDefinitionListener;

  public void setGoToDefinitionListener(ThemeEditorComponent.GoToListener goToDefinitionListener) {
    myGoToDefinitionListener = goToDefinitionListener;
  }

  private class GoToDefinitionAction implements ActionListener {
    private EditedStyleItem myItem = null;

    public void setItem(EditedStyleItem item) {
      this.myItem = item;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myGoToDefinitionListener != null && myItem != null) {
        myGoToDefinitionListener.goTo(myItem);
      }
    }
  }

  private class OpenFileAction implements ActionListener {
    private VirtualFile myFile = null;

    public void setFile(VirtualFile file) {
      myFile = file;
    }

    private final Runnable myOpenFileRunnable = new Runnable() {
      @Override
      public void run() {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, myFile);
        FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
      }
    };

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myFile != null) {
        ApplicationManager.getApplication().invokeLater(myOpenFileRunnable);
      }
    }
  }

  private final GoToDefinitionAction myGoToDefinitionAction = new GoToDefinitionAction();
  private final OpenFileAction myOpenFileAction = new OpenFileAction();
  private final ActionListener myGotoParentAction = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      myGoToDefinitionListener.goToParent();
    }
  };

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

  public AttributesTableModel(@NotNull ThemeEditorStyle selectedStyle,
                              @NotNull AttributesGrouper.GroupBy groupBy,
                              @NotNull Configuration configuration,
                              Project project) {
    myProject = project;
    myAttributes = new ArrayList<EditedStyleItem>();
    myLabels = new ArrayList<TableLabel>();
    mySelectedStyle = selectedStyle;
    myGroupBy = groupBy;
    myResourceResolver = configuration.getResourceResolver();
    reloadContent();
  }

  private void reloadContent() {
    final List<EditedStyleItem> rawAttributes = ThemeEditorUtils.resolveAllAttributes(mySelectedStyle);
    myAttributes.clear();
    myLabels = AttributesGrouper.generateLabels(myGroupBy, rawAttributes, myAttributes);
    fireTableStructureChanged();
  }

  public RowContents getRowContents(final int rowIndex) {
    if (rowIndex == 0) {
      return parentAttribute;
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
    return 2;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return getRowContents(rowIndex).getValueAt(columnIndex);
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

  protected boolean isReadOnly() {
    return false;
  }

  @NotNull
  public ThemeEditorStyle getSelectedStyle() {
    return mySelectedStyle;
  }

  /**
   * Basically a union type, RowContents = LabelContents | AttributeContents | ParentAttribute
   */
  public interface RowContents {
    int getColumnSpan(int column);

    Object getValueAt(int column);

    void setValueAt(int column, String value);

    Class<?> getCellClass(int column);

    boolean isCellEditable(int column);

    /**
     * Attributes table has a pop-up menu which contains item "Go to definition",
     * which should work differently for different rows. This method should return
     * ActionListener which would implement "Go to definition" functionality.
     * Listener would be set as JMenuItem action listener before returning menu from
     * {@link com.android.tools.idea.editors.theme.ThemeEditorTable#getComponentPopupMenu}.
     *
     * @return null when no "Go to definition" action for current row is available
     */
    ActionListener getGoToDefinitionCallback();

    ActionListener getResetCallback();
  }

  public class ParentAttribute implements RowContents {

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
        ThemeEditorStyle parent = mySelectedStyle.getParent();
        return parent == null ? "[no parent]" : parent;
      }
    }

    @Override
    public void setValueAt(int column, String newName) {
      if (column == 0) {
        throw new RuntimeException("Tried to setValue at parent attribute label");
      }
      else {
        ThemeEditorStyle parent = mySelectedStyle.getParent();
        if (parent == null || !parent.getQualifiedName().equals(newName)) {
          //Changes the value of Parent in XML
          mySelectedStyle.setParent(newName);
          fireTableCellUpdated(0, 1);
        }
      }
    }

    @Override
    public Class<?> getCellClass(int column) {
      return column == 0 ? String.class : ParentAttribute.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      return (column == 1 && !mySelectedStyle.isReadOnly());
    }

    @Override
    public ActionListener getGoToDefinitionCallback() {
      return mySelectedStyle.getParent() == null ? null : myGotoParentAction;
    }

    @Override
    public ActionListener getResetCallback() {
      return null;
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
    public void setValueAt(int column, String value) {
      throw new RuntimeException(String.format("Tried to setValue at immutable label row of LabelledModel, column = %1$d" + column));
    }

    @Override
    public ActionListener getGoToDefinitionCallback() {
      return null;
    }

    @Override
    public ActionListener getResetCallback() {
      return null;
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
      return myAttributes.get(myRowIndex);
    }

    @Override
    public Class<?> getCellClass(int column) {
      EditedStyleItem item = myAttributes.get(myRowIndex);

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

      AttributeDefinition attrDefinition =
        StyleResolver.getAttributeDefinition(mySelectedStyle.getConfiguration(), item.getItemResourceValue());

      if (urlType == ResourceType.COLOR
              || (value != null && value.startsWith("#") && ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Color))) {
        return Color.class;
      }

      if (urlType == ResourceType.STYLE) {
        return ThemeEditorStyle.class;
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

      if (urlType == ResourceType.BOOL
          || (("true".equals(value) || "false".equals(value))
              && ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Boolean))) {
        return Boolean.class;
      }

      return EditedStyleItem.class;
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
          listener.attributeChangedOnReadOnlyTheme((EditedStyleItem)getValueAt(1), value);
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

    @Override
    public ActionListener getGoToDefinitionCallback() {
      EditedStyleItem item = (EditedStyleItem)getValueAt(1);
      if (getCellClass(1) == ThemeEditorStyle.class) {
        myGoToDefinitionAction.setItem(item);
        return myGoToDefinitionAction;
      }

      VirtualFileManager manager = VirtualFileManager.getInstance();
      ResourceValue resourceValue = myResourceResolver.resolveResValue(item.getItemResourceValue());
      final File file = new File(resourceValue.getValue());

      final VirtualFile virtualFile = file.exists() ? manager.findFileByUrl("file://" + file.getAbsolutePath()) : null;
      if (virtualFile != null) {
        myOpenFileAction.setFile(virtualFile);
        return myOpenFileAction;
      }

      return null;
    }

    /**
     * Creates and returns an ActionListener that suppresses an attribute defined in the current theme
     * from that theme, hence returning to inheriting it from the parent theme.
     * Returns null if the attribute in question is not defined in the current theme, or is read-only
     */
    @Override
    public ActionListener getResetCallback() {
      final EditedStyleItem item = (EditedStyleItem) getValueAt(0);
      if (!mySelectedStyle.isReadOnly() && mySelectedStyle.equals(item.getSourceStyle())) {
        return new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            mySelectedStyle.removeAttribute(item.getQualifiedName());
            fireTableCellUpdated(myRowIndex, 0);
          }
        };
      }
      return null;
    }
  }
}
