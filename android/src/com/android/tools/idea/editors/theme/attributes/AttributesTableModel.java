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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.editors.theme.StyleResolver;
import com.android.tools.idea.editors.theme.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.editors.AttributeReferenceRendererEditor;
import com.google.common.collect.ImmutableList;
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

import javax.swing.event.TableModelEvent;
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
  private final static Logger LOG = Logger.getInstance(AttributesTableModel.class);

  /** Cells containing values with classes in WIDE_CLASSES are going to have column span 2 */
  private static final Set<Class<?>> WIDE_CLASSES = ImmutableSet.of(Color.class, DrawableDomElement.class);

  protected final List<EditedStyleItem> myAttributes;
  private List<TableLabel> myLabels;
  /**
   * Used to store the name of the theme as it is in the styles.xml file
   * May be different from the name of mySelectedStyle as a reloading of the theme may be necessary
   */
  private String myThemeNameInXml;
  /**
   * Used to store the name of the theme parent as it is in the styles.xml file
   * May be different from the name of mySelectedStyle.getParent() as a reloading of the theme may be necessary
   */
  private String myParentNameInXml;

  protected final ThemeEditorStyle mySelectedStyle;

  private final AttributesGrouper.GroupBy myGroupBy;
  private final ResourceResolver myResourceResolver;
  private final Project myProject;

  private final List<ThemePropertyChangedListener> myThemePropertyChangedListeners = new ArrayList<ThemePropertyChangedListener>();
  public final ParentAttribute parentAttribute = new ParentAttribute();
  private final List<RowContents> mySpecialRows = ImmutableList.of(
    new ThemeNameAttribute(),
    parentAttribute
  );

  private AttributeReferenceRendererEditor.ClickListener myGoToDefinitionListener;

  public void setGoToDefinitionListener(AttributeReferenceRendererEditor.ClickListener goToDefinitionListener) {
    myGoToDefinitionListener = goToDefinitionListener;
  }

  private class GoToDefinitionActionListener implements ActionListener {
    private EditedStyleItem myItem = null;

    public void setItem(EditedStyleItem item) {
      this.myItem = item;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myGoToDefinitionListener != null && myItem != null) {
        myGoToDefinitionListener.clicked(myItem);
      }
    }
  }

  private class OpenFileActionListener implements ActionListener {
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

  private final GoToDefinitionActionListener myGoToDefinitionActionListener = new GoToDefinitionActionListener();
  private final OpenFileActionListener myOpenFileActionListener = new OpenFileActionListener();

  public interface ThemePropertyChangedListener {
    void attributeChangedOnReadOnlyTheme(final EditedStyleItem attribute, final String newValue);
  }

  public void addThemePropertyChangedListener(final ThemePropertyChangedListener listener) {
    myThemePropertyChangedListeners.add(listener);
  }

  public String getThemeNameInXml() {
    return myThemeNameInXml;
  }

  public AttributesTableModel(@NotNull ThemeEditorStyle selectedStyle,
                              @NotNull AttributesGrouper.GroupBy groupBy,
                              @NotNull ResourceResolver resourceResolver,
                              Project project) {
    myProject = project;
    myAttributes = new ArrayList<EditedStyleItem>();
    myLabels = new ArrayList<TableLabel>();
    mySelectedStyle = selectedStyle;
    myGroupBy = groupBy;
    myThemeNameInXml = mySelectedStyle.getName();
    myResourceResolver = resourceResolver;

    ThemeEditorStyle parent = selectedStyle.getParent();
    myParentNameInXml = (parent != null) ? parent.getName() : null;
    reloadContent();
  }

  private void reloadContent() {
    final List<EditedStyleItem> rawAttributes = ThemeEditorUtils.resolveAllAttributes(mySelectedStyle);
    myAttributes.clear();
    myLabels = AttributesGrouper.generateLabels(myGroupBy, rawAttributes, myAttributes);
    fireTableStructureChanged();
  }

  public RowContents getRowContents(final int rowIndex) {
    if (rowIndex < mySpecialRows.size()) {
      return mySpecialRows.get(rowIndex);
    }

    int offset = mySpecialRows.size();
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

  @NotNull
  public ThemeEditorStyle getSelectedStyle() {
    return mySelectedStyle;
  }

  /**
   * Basically a union type, RowContents = LabelContents | AttributeContents | ParentAttribute | ThemeNameAttribute
   */
  public interface RowContents {
    int getColumnSpan(int column);

    Object getValueAt(int column);

    void setValueAt(int column, Object value);

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

  /**
   * Deals with setting up the theme name as an attribute that can be changed
   */
  private class ThemeNameAttribute implements RowContents {
    @Override
    public int getColumnSpan(int column) {
      return 1;
    }

    @Override
    public Object getValueAt(int column) {
      if (column == 0) {
        return "Theme Name";
      }
      else {
        return mySelectedStyle.getSimpleName();
      }
    }

    @Override
    public void setValueAt(int column, Object value) {
      if (column == 0) {
        throw new RuntimeException("Tried to setValue at parent attribute label");
      }
      else {
        String newName = (String) value;
        mySelectedStyle.setName(newName);
        myThemeNameInXml = SdkConstants.STYLE_RESOURCE_PREFIX + newName;

        fireTableChanged(new TableModelEvent(AttributesTableModel.this, 0));
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

    @Override
    public ActionListener getGoToDefinitionCallback() {
      return null;
    }

    @Override
    public ActionListener getResetCallback() {
      return null;
    }
  }

  public class ParentAttribute implements RowContents {
    private ActionListener myGotoDefinitionCallback;

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
        return myParentNameInXml == null ? "[no parent]" : myParentNameInXml;
      }
    }

    @Override
    public void setValueAt(int column, Object value) {
      if (column == 0) {
        throw new RuntimeException("Tried to setValue at parent attribute label");
      }
      else {
        String newName = (String) value;
        //Changes the value of Parent in XML
        mySelectedStyle.setParent(newName);
        myParentNameInXml = newName;
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

    public void setGotoDefinitionCallback(ActionListener gotoDefinitionCallback) {
      myGotoDefinitionCallback = gotoDefinitionCallback;
    }

    @Override
    public ActionListener getGoToDefinitionCallback() {
      return myParentNameInXml == null ? null : myGotoDefinitionCallback;
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
    public void setValueAt(int column, Object value) {
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

      if (column == 1) {
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

      EditedStyleItem item = myAttributes.get(myRowIndex);
      // Color rows are editable. Also the middle column for all other attributes.
      return (WIDE_CLASSES.contains(getCellClass(column)) || column == 1) && item.isPublicAttribute();
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

    @Override
    public ActionListener getGoToDefinitionCallback() {
      EditedStyleItem item = (EditedStyleItem)getValueAt(1);
      if (getCellClass(1) == ThemeEditorStyle.class) {
        myGoToDefinitionActionListener.setItem(item);
        return myGoToDefinitionActionListener;
      }

      VirtualFileManager manager = VirtualFileManager.getInstance();
      ResourceValue resourceValue = myResourceResolver.resolveResValue(item.getItemResourceValue());
      final File file = new File(resourceValue.getValue());

      final VirtualFile virtualFile = file.exists() ? manager.findFileByUrl("file://" + file.getAbsolutePath()) : null;
      if (virtualFile != null) {
        myOpenFileActionListener.setFile(virtualFile);
        return myOpenFileActionListener;
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
