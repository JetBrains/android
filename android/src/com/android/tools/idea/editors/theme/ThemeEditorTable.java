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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.attributes.ShowJavadocAction;
import com.android.tools.idea.editors.theme.attributes.TableLabel;
import com.android.tools.idea.editors.theme.attributes.editors.*;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.android.dom.resources.Flag;
import org.jetbrains.annotations.NotNull;
import spantable.CellSpanModel;
import spantable.CellSpanTable;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Map;

public class ThemeEditorTable extends CellSpanTable {
  private Map<Class<?>, Integer> myClassHeights;
  private ShowJavadocAction myJavadocAction;
  private ThemeEditorComponent.GoToListener myGoToListener;
  private ThemeEditorContext myContext;

  /**
   * label text consistent with rest of IDE (e.g. right click)
   */
  private static final String GO_TO_DECLARATION = "Go To Declaration";

  public ThemeEditorTable() {
    putClientProperty("terminateEditOnFocusLost", true);
    // We shouldn't allow autoCreateColumnsFromModel, because when setModel() will be invoked, it removes
    // existing listeners to cell editors.
    setAutoCreateColumnsFromModel(false);

    for (int c = 0; c < AttributesTableModel.COL_COUNT; ++c) {
      addColumn(new TableColumn(c));
    }
  }

  public void setGoToListener(@NotNull ThemeEditorComponent.GoToListener goToListener) {
    myGoToListener = goToListener;
  }

  public void customizeTable(@NotNull ThemeEditorContext context,
                             @NotNull AndroidThemePreviewPanel previewPanel,
                             @NotNull ParentRendererEditor.ThemeParentChangedListener themeParentChangedListener) {
    myContext = context;
    myJavadocAction = new ShowJavadocAction(this, myContext);
    setRenderersAndEditors(previewPanel, themeParentChangedListener);
  }

  @Override
  public JPopupMenu getComponentPopupMenu() {
    // Workaround for http://b.android.com/173610
    // getMousePosition returns null sometimes even when the component is visible and the mouse is over the component. This seems to be a
    // bug in LWWindowPeer after a modal dialog has been displayed.
    Point point = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return getPopupMenuAtCell(rowAtPoint(point), columnAtPoint(point));
  }

  @Override
  public void setRowSorter(RowSorter<? extends TableModel> sorter) {
    super.setRowSorter(sorter);
    updateRowHeights();
  }

  public void updateRowHeights() {
    TableModel rawModel = getModel();
    if (!(rawModel instanceof CellSpanModel)) {
      return;
    }

    CellSpanModel myModel = (CellSpanModel)rawModel;

    int defaultRowHeight = myClassHeights.get(Object.class);
    setRowHeight(defaultRowHeight);
    for (int row = 0; row < myModel.getRowCount(); row++) {
      // Find the maximum row height
      int maxRowHeight = -1;
      for (int col = 0; col < myModel.getColumnCount(); col += myModel.getColumnSpan(row, col)) {
        Class<?> cellClass = myModel.getCellClass(row, col);
        Integer rowHeight = myClassHeights.get(cellClass);

        if (rowHeight != null) {
          maxRowHeight = Math.max(maxRowHeight, rowHeight);
        }
      }

      if (maxRowHeight == -1) {
        // Leave the default size
        continue;
      }
      int viewRow = convertRowIndexToView(row);

      if (viewRow != -1) {
        setRowHeight(viewRow, maxRowHeight);
      }
    }
  }

  public void setClassHeights(Map<Class<?>, Integer> classHeights) {
    myClassHeights = classHeights;
    updateRowHeights();
  }

  private void setRenderersAndEditors(@NotNull AndroidThemePreviewPanel previewPanel,
                                      @NotNull ParentRendererEditor.ThemeParentChangedListener themeParentChangedListener) {
    Project project = myContext.getProject();
    ResourcesCompletionProvider completionProvider = new ResourcesCompletionProvider(myContext);
    final AttributeReferenceRendererEditor styleEditor = new AttributeReferenceRendererEditor(project, completionProvider);

    setDefaultRenderer(Color.class, new DelegatingCellRenderer(new ColorRendererEditor(myContext, previewPanel, false)));
    setDefaultRenderer(EditedStyleItem.class, new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    setDefaultRenderer(ConfiguredThemeEditorStyle.class,
                       new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    setDefaultRenderer(String.class, new DelegatingCellRenderer(getDefaultRenderer(String.class)));
    setDefaultRenderer(Integer.class, new DelegatingCellRenderer(new IntegerRenderer()));
    setDefaultRenderer(Boolean.class, new DelegatingCellRenderer(new BooleanRendererEditor(myContext)));
    setDefaultRenderer(Enum.class, new DelegatingCellRenderer(new EnumRendererEditor()));
    setDefaultRenderer(Flag.class, new DelegatingCellRenderer(new FlagRendererEditor()));
    setDefaultRenderer(AttributesTableModel.ParentAttribute.class, new DelegatingCellRenderer(new ParentRendererEditor(myContext, themeParentChangedListener)));
    setDefaultRenderer(DrawableDomElement.class, new DelegatingCellRenderer(new DrawableRendererEditor(myContext, previewPanel, false)));
    setDefaultRenderer(TableLabel.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        Font regularFont = UIUtil.getLabelFont();
        int regularFontSize = getFontMetrics(regularFont).getHeight();
        Font headerFont = regularFont.deriveFont(regularFontSize * ThemeEditorConstants.ATTRIBUTES_HEADER_FONT_SCALE);
        this.setFont(headerFont);
        return this;
      }
    });

    setDefaultEditor(Color.class, new DelegatingCellEditor(false, new ColorRendererEditor(myContext, previewPanel, true)));
    setDefaultEditor(EditedStyleItem.class,
                     new DelegatingCellEditor(false, new AttributeReferenceRendererEditor(project, completionProvider)));
    setDefaultEditor(String.class, new DelegatingCellEditor(false, getDefaultEditor(String.class)));
    setDefaultEditor(Integer.class, new DelegatingCellEditor(getDefaultEditor(Integer.class)));
    setDefaultEditor(Boolean.class, new DelegatingCellEditor(false, new BooleanRendererEditor(myContext)));
    setDefaultEditor(Enum.class, new DelegatingCellEditor(false, new EnumRendererEditor()));
    setDefaultEditor(Flag.class, new DelegatingCellEditor(false, new FlagRendererEditor()));
    setDefaultEditor(AttributesTableModel.ParentAttribute.class, new DelegatingCellEditor(false, new ParentRendererEditor(myContext, themeParentChangedListener)));

    // We allow to edit style pointers as Strings.
    setDefaultEditor(ConfiguredThemeEditorStyle.class, new DelegatingCellEditor(false, styleEditor));
    setDefaultEditor(DrawableDomElement.class, new DelegatingCellEditor(false, new DrawableRendererEditor(myContext, previewPanel, true)));
  }

  private JPopupMenu getPopupMenuAtCell(final int row, final int column) {
    if (row < 0 || column < 0) {
      return null;
    }

    TableModel rawModel = getModel();
    if (!(rawModel instanceof AttributesTableModel)) {
      return null;
    }

    final AttributesTableModel model = (AttributesTableModel)rawModel;
    AttributesTableModel.RowContents contents = model.getRowContents(this.convertRowIndexToModel(row));

    if (contents instanceof AttributesTableModel.AttributeContents) {
      final AttributesTableModel.AttributeContents attribute = (AttributesTableModel.AttributeContents)contents;

      final EditedStyleItem item = attribute.getValue();
      if (item == null) {
        return null;
      }

      final JBPopupMenu popupMenu = new JBPopupMenu();
      if (attribute.getCellClass(1) == ConfiguredThemeEditorStyle.class) {
        popupMenu.add(new AbstractAction(GO_TO_DECLARATION) {
          @Override
          public void actionPerformed(ActionEvent e) {
            myGoToListener.goTo(item);
          }
        });
      }
      else {
        final ResourceResolver resolver = myContext.getResourceResolver();
        assert resolver != null;
        final Project project = myContext.getProject();
        final ResourceValue resourceValue = resolver.resolveResValue(item.getSelectedValue());
        final File file = new File(resourceValue.getValue());

        final VirtualFileManager manager = VirtualFileManager.getInstance();
        final VirtualFile virtualFile = file.exists() ? manager.findFileByUrl("file://" + file.getAbsolutePath()) : null;
        if (virtualFile != null) {
          popupMenu.add(new AbstractAction(GO_TO_DECLARATION) {
            @Override
            public void actionPerformed(ActionEvent e) {
              final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
              FileEditorManager.getInstance(project).openEditor(descriptor, true);
            }
          });
        }
      }

      myJavadocAction.setCurrentItem(item);
      popupMenu.add(myJavadocAction);

      final ConfiguredThemeEditorStyle selectedStyle = model.getSelectedStyle();
      if (!selectedStyle.isReadOnly() && selectedStyle.hasItem(item)) {
        popupMenu.add(new AbstractAction("Reset value") {
          @Override
          public void actionPerformed(ActionEvent e) {
            selectedStyle.removeAttribute(item.getQualifiedName());
            model.fireTableCellUpdated(attribute.getRowIndex(), 0);
          }
        });
      }

      return popupMenu;
    }
    else if (contents instanceof AttributesTableModel.ParentAttribute) {
      final ConfiguredThemeEditorStyle parentStyle = model.getSelectedStyle().getParent();
      if (parentStyle == null) {
        return null;
      }

      final JBPopupMenu menu = new JBPopupMenu();
      menu.add(new AbstractAction(GO_TO_DECLARATION) {
        @Override
        public void actionPerformed(ActionEvent e) {
          myGoToListener.goToParent();
        }
      });

      return menu;
    }

    return null;
  }

  /**
   * Prevents the automatic setting of a background color by the L&F
   */
  @Override
  public void setBackground(Color color) {
    if (color instanceof ColorUIResource) {
      color = null;
    }
    super.setBackground(color);
  }
}
