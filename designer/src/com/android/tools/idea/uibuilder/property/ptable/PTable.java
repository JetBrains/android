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
package com.android.tools.idea.uibuilder.property.ptable;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.property.ptable.renderers.PNameRenderer;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TableUI;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Objects;

public class PTable extends JBTable implements DataProvider, DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  private final PNameRenderer myNameRenderer = new PNameRenderer();
  private final TableSpeedSearch mySpeedSearch;
  private PTableModel myModel;
  private CopyPasteManager myCopyPasteManager;
  private PTableCellEditorProvider myEditorProvider;

  private int myMouseHoverRow;
  private int myMouseHoverCol;
  private Point myMouseHoverPoint;

  public PTable(@NotNull PTableModel model) {
    this(model, CopyPasteManager.getInstance());
  }

  @VisibleForTesting
  PTable(@NotNull PTableModel model, @NotNull CopyPasteManager copyPasteManager) {
    super(model);
    myCopyPasteManager = copyPasteManager;
    myMouseHoverPoint = new Point(-1, -1);

    // since the row heights are uniform, there is no need to look at more than a few items
    setMaxItemsForSizeCalculation(5);

    // When a label cannot be fully displayed, hovering over it results in a popup that extends beyond the
    // cell bounds to show the full value. We don't need this feature as it'll end up covering parts of the
    // cell we don't want covered.
    setExpandableItemsEnabled(false);

    setShowColumns(false);
    setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setShowVerticalLines(true);
    setIntercellSpacing(new Dimension(0, 1));
    setGridColor(UIUtil.getSlightlyDarkerColor(getBackground()));

    setColumnSelectionAllowed(false);
    setCellSelectionEnabled(false);
    setRowSelectionAllowed(true);

    addMouseListener(new MouseTableListener());

    HoverListener hoverListener = new HoverListener();
    addMouseMotionListener(hoverListener);
    addMouseListener(hoverListener);

    mySpeedSearch = new TableSpeedSearch(this, (object, cell) -> {
      if (cell.column != 0) return null; // only match property names, not values
      return object instanceof PTableItem ? ((PTableItem)object).getName() : null;
    });
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    myModel = (PTableModel)model;
    super.setModel(model);
  }

  @Override
  public PTableModel getModel() {
    return (PTableModel)super.getModel();
  }

  public void setEditorProvider(PTableCellEditorProvider editorProvider) {
    myEditorProvider = editorProvider;
  }

  // Bug: 221565
  // Without this line it is impossible to get focus to a combo box editor.
  // The code in JBTable will move the focus to the JPanel that includes
  // the combo box, the resource button, and the design button.
  @Override
  public boolean surrendersFocusOnKeyStroke() {
    return false;
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    if (column == 0) {
      return myNameRenderer;
    }

    PTableItem value = (PTableItem)getValueAt(row, column);
    return value.getCellRenderer();
  }

  @Override
  public PTableCellEditor getCellEditor(int row, int column) {
    PTableItem value = (PTableItem)getValueAt(row, column);
    if (value != null && myEditorProvider != null) {
      return myEditorProvider.getCellEditor(value);
    }
    return null;
  }

  public TableSpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }

  public boolean isHover(int row, int col) {
    return row == myMouseHoverRow && col == myMouseHoverCol;
  }

  @NotNull
  public Point getHoverPosition() {
    return myMouseHoverPoint;
  }

  @Override
  public void setUI(TableUI ui) {
    super.setUI(ui);

    // Setup focus traversal keys such that tab takes focus out of the table
    setFocusTraversalKeys(
      KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
      KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
    setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, KeyboardFocusManager.getCurrentKeyboardFocusManager()
      .getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));

    // Customize keymaps. See https://docs.oracle.com/javase/tutorial/uiswing/misc/keybinding.html for info on how this works, but the
    // summary is that we set an input map mapping key bindings to a string, and an action map that maps those strings to specific actions.
    ActionMap actionMap = getActionMap();
    InputMap focusedInputMap = getInputMap(JComponent.WHEN_FOCUSED);
    InputMap ancestorInputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "smartEnter");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    actionMap.put("smartEnter", new MyEnterAction(false));

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleEditor");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    actionMap.put("toggleEditor", new MyEnterAction(true));

    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "expandCurrentRight");
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), "expandCurrentRight");
    actionMap.put("expandCurrentRight", new MyExpandCurrentAction(true));

    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "collapseCurrentLeft");
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), "collapseCurrentLeft");
    actionMap.put("collapseCurrentLeft", new MyExpandCurrentAction(false));
  }

  private void toggleTreeNode(int row) {
    PTableItem item = (PTableItem)getValueAt(row, 0);
    int index = convertRowIndexToModel(row);
    if (item.isExpanded()) {
      myModel.collapse(index);
    }
    else {
      myModel.expand(index);
    }
  }

  private void toggleStar(int row) {
    PTableItem item = (PTableItem)getValueAt(row, 0);
    StarState state = item.getStarState();
    if (state != StarState.NOT_STAR_ABLE) {
      item.setStarState(state.opposite());
    }
  }

  private void selectRow(int row) {
    getSelectionModel().setSelectionInterval(row, row);
    TableUtil.scrollSelectionToVisible(this);
  }

  private void quickEdit(int row) {
    PTableCellEditor editor = getCellEditor(row, 0);
    if (editor == null) {
      return;
    }

    // only perform edit if we know the editor is capable of a quick toggle action.
    // We know that boolean editors switch their state and finish editing right away
    if (editor.isBooleanEditor()) {
      startEditing(row);
    }
  }

  private void startEditing(int row) {
    PTableCellEditor editor = getCellEditor(row, 0);
    if (editor == null) {
      return;
    }

    editCellAt(row, 1);

    JComponent preferredComponent = getComponentToFocus(editor);
    if (preferredComponent == null) return;

    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      preferredComponent.requestFocusInWindow();
      editor.activate();
    });
  }

  @Nullable
  private JComponent getComponentToFocus(PTableCellEditor editor) {
    JComponent preferredComponent = editor.getPreferredFocusComponent();
    if (preferredComponent == null) {
      preferredComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)editorComp);
    }
    if (preferredComponent == null) {
      return null;
    }
    return preferredComponent;
  }

  public void restoreSelection(int previousSelectedRow, @Nullable PTableItem previousSelectedItem) {
    int selectedRow = 0;
    if (previousSelectedItem != null) {
      PTableItem item =
        previousSelectedRow >= 0 && previousSelectedRow < getRowCount() ? (PTableItem)getValueAt(previousSelectedRow, 0) : null;
      if (Objects.equals(item, previousSelectedItem)) {
        selectedRow = previousSelectedRow;
      }
      else {
        for (int row = 0; row < getRowCount(); row++) {
          item = (PTableItem)getValueAt(row, 0);
          if (item.equals(previousSelectedItem)) {
            selectedRow = row;
            break;
          }
        }
      }
    }
    if (selectedRow < getRowCount()) {
      addRowSelectionInterval(selectedRow, selectedRow);
    }
  }

  @Nullable
  public PTableItem getSelectedItem() {
    int selectedRow = getSelectedRow();
    if (isEditing() || selectedRow == -1) {
      return null;
    }
    return (PTableItem)getValueAt(selectedRow, 0);
  }

  @Nullable
  private PTableItem getSelectedNonGroupItem() {
    PTableItem item = getSelectedItem();
    return item instanceof PTableGroupItem ? null : item;
  }

  // ---- Implements DataProvider ----

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  // ---- Implements CopyProvider ----

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return getSelectedNonGroupItem() != null;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    PTableItem item = getSelectedNonGroupItem();
    if (item == null) {
      return;
    }
    myCopyPasteManager.setContents(new StringSelection(item.getValue()));
  }

  // ---- Implements CutProvider ----

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    return getSelectedNonGroupItem() != null;
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {
    if (getSelectedNonGroupItem() == null) {
      return;
    }
    performCopy(dataContext);
    deleteElement(dataContext);
  }

  // ---- Implements DeleteProvider ----

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return getSelectedItem() != null;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    PTableItem item = getSelectedItem();
    if (item == null) {
      return;
    }
    if (item instanceof PTableGroupItem) {
      deleteGroupValues(dataContext, (PTableGroupItem)item);
    }
    else {
      item.setValue(null);
    }
  }

  private static void deleteGroupValues(@NotNull DataContext dataContext, @NotNull PTableGroupItem group) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) {
      return;
    }
    PsiFile containingFile = PsiManager.getInstance(project).findFile(file);
    if (containingFile == null) {
      return;
    }
    new WriteCommandAction.Simple(project, "Delete " + group.getName(), containingFile) {
      @Override
      protected void run() throws Throwable {
        group.getChildren().forEach(item -> item.setValue(null));
      }
    }.execute();
  }

  // ---- Implements PasteProvider ----

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    if (getSelectedNonGroupItem() == null) {
      return false;
    }
    Transferable transferable = myCopyPasteManager.getContents();
    return transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor);
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    PTableItem item = getSelectedNonGroupItem();
    if (item == null) {
      return;
    }
    Transferable transferable = myCopyPasteManager.getContents();
    if (transferable == null || !transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      return;
    }
    try {
      item.setValue(transferable.getTransferData(DataFlavor.stringFlavor));
    }
    catch (IOException | UnsupportedFlavorException exception) {
      Logger.getInstance(PTable.class).warn(exception);
    }
  }

  // Expand/Collapse if it is a group property, start editing otherwise
  private class MyEnterAction extends AbstractAction {
    // don't launch a full editor, just perform a quick toggle
    private final boolean myToggleOnly;

    public MyEnterAction(boolean toggleOnly) {
      myToggleOnly = toggleOnly;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      PTableItem item = (PTableItem)getValueAt(selectedRow, 0);
      if (item.hasChildren()) {
        toggleTreeNode(selectedRow);
        selectRow(selectedRow);
      }
      else if (myToggleOnly) {
        quickEdit(selectedRow);
      }
      else {
        startEditing(selectedRow);
      }
    }
  }

  // Expand/Collapse items on right/left key press
  private class MyExpandCurrentAction extends AbstractAction {
    private final boolean myExpand;

    public MyExpandCurrentAction(boolean expand) {
      myExpand = expand;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      PTableItem item = (PTableItem)getValueAt(selectedRow, 0);
      int index = convertRowIndexToModel(selectedRow);
      if (myExpand) {
        if (item.hasChildren() && !item.isExpanded()) {
          myModel.expand(index);
          selectRow(selectedRow);
        }
      }
      else {
        if (item.isExpanded()) { // if it is a compound node, collapse it
          myModel.collapse(index);
          selectRow(selectedRow);
        }
        else if (item.getParent() != null) { // if it is a child node, move selection to the parent
          selectRow(myModel.getParent(index));
        }
      }
    }
  }

  // Expand/Collapse group items on mouse click
  private class MouseTableListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      int row = rowAtPoint(e.getPoint());
      if (row == -1) {
        return;
      }

      PTableItem item = (PTableItem)getValueAt(row, 0);

      Rectangle rectLeftColumn = getCellRect(row, convertColumnIndexToView(0), false);
      if (rectLeftColumn.contains(e.getX(), e.getY())) {
        if (PNameRenderer.hitTestTreeNodeIcon(item, e.getX() - rectLeftColumn.x) && item.hasChildren()) {
          toggleTreeNode(row);
          return;
        }
        if (PNameRenderer.hitTestStarIcon(e.getX() - rectLeftColumn.x)) {
          toggleStar(row);
          return;
        }
      }

      Rectangle rectRightColumn = getCellRect(row, convertColumnIndexToView(1), false);
      if (rectRightColumn.contains(e.getX(), e.getY())) {
        item.mousePressed(e, rectRightColumn);
      }
    }
  }

  // Repaint cells on mouse hover
  private class HoverListener extends MouseAdapter {
    private int myPreviousHoverRow = -1;
    private int myPreviousHoverCol = -1;

    @Override
    public void mouseMoved(MouseEvent e) {
      myMouseHoverPoint = e.getPoint();
      myMouseHoverRow = rowAtPoint(e.getPoint());
      if (myMouseHoverRow >= 0) {
        myMouseHoverCol = columnAtPoint(e.getPoint());
      }

      // remove hover from the previous cell
      if (myPreviousHoverRow != -1 && (myPreviousHoverRow != myMouseHoverRow || myPreviousHoverCol != myMouseHoverCol)) {
        repaint(getCellRect(myPreviousHoverRow, myPreviousHoverCol, true));
        myPreviousHoverRow = -1;
      }

      if (myMouseHoverCol < 0) {
        return;
      }

      // repaint cell that has the hover
      repaint(getCellRect(myMouseHoverRow, myMouseHoverCol, true));

      myPreviousHoverRow = myMouseHoverRow;
      myPreviousHoverCol = myMouseHoverCol;
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (myMouseHoverRow != -1 && myMouseHoverCol != -1) {
        Rectangle cellRect = getCellRect(myMouseHoverRow, 1, true);
        myMouseHoverRow = myMouseHoverCol = -1;
        repaint(cellRect);
      }
    }
  }
}
