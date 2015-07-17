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
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import spantable.CellSpanModel;
import spantable.CellSpanTable;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Map;

public class ThemeEditorTable extends CellSpanTable {
  private Map<Class<?>, Integer> myClassHeights;
  private ShowJavadocAction myJavadocAction;
  private ThemeEditorComponent.GoToListener myGoToListener;
  private ThemeEditorContext myContext;

  public ThemeEditorTable() {
    putClientProperty("terminateEditOnFocusLost", true);
  }

  public void setGoToListener(@NotNull ThemeEditorComponent.GoToListener goToListener) {
    myGoToListener = goToListener;
  }

  public void setContext(@NotNull ThemeEditorContext context) {
    myContext = context;
    myJavadocAction = new ShowJavadocAction(this, myContext);
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

    setRowHeight(myClassHeights.get(Object.class));
    for (int row = 0; row < myModel.getRowCount(); row++) {
      final Class<?> cellClass = myModel.getCellClass(row, 0);
      final Integer rowHeight = myClassHeights.get(cellClass);
      if (rowHeight != null) {
        int viewRow = convertRowIndexToView(row);

        if (viewRow != -1) {
          setRowHeight(viewRow, rowHeight);
        }
      }
    }
  }

  public void setClassHeights(Map<Class<?>, Integer> classHeights) {
    myClassHeights = classHeights;
    updateRowHeights();
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
      final AttributesTableModel.AttributeContents attribute = (AttributesTableModel.AttributeContents) contents;

      final EditedStyleItem item = attribute.getValueAt(1);
      if (item == null) {
        return null;
      }

      final JBPopupMenu popupMenu = new JBPopupMenu();
      if (attribute.getCellClass(1) == ThemeEditorStyle.class) {
        popupMenu.add(new AbstractAction("Go to definition") {
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
          popupMenu.add(new AbstractAction("Go to definition") {
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

      final ThemeEditorStyle selectedStyle = model.getSelectedStyle();
      if (selectedStyle.isReadOnly() || !selectedStyle.equals(item.getSourceStyle())) {
        return popupMenu;
      }

      popupMenu.add(new AbstractAction("Reset value") {
        @Override
        public void actionPerformed(ActionEvent e) {
          selectedStyle.removeAttribute(item.getQualifiedName());
          model.fireTableCellUpdated(attribute.getRowIndex(), 0);
        }
      });
      return popupMenu;
    }
    else if (contents instanceof AttributesTableModel.ParentAttribute) {
      final ThemeEditorStyle parentStyle = model.getSelectedStyle().getParent();
      if (parentStyle == null) {
        return null;
      }

      final JBPopupMenu menu = new JBPopupMenu();
      menu.add(new AbstractAction("Edit parent") {
        @Override
        public void actionPerformed(ActionEvent e) {
          myGoToListener.goToParent();
        }
      });

      return menu;
    }

    return null;
  }
}
