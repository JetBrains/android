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
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.Hint;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.TableUI;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.io.IOException;
import java.util.Objects;

public class PTable extends JBTable implements DataProvider, DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  private PTableModel myModel;
  private CopyPasteManager myCopyPasteManager;
  private PTableCellRendererProvider myRendererProvider;
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
    myRendererProvider = new PTableDefaultCellRendererProvider();

    // since the row heights are uniform, there is no need to look at more than a few items
    setMaxItemsForSizeCalculation(5);

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

    addKeyListener(new PTableKeyListener(this));
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

  public void setRendererProvider(@NotNull PTableCellRendererProvider rendererProvider) {
    myRendererProvider = rendererProvider;
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

  // The method editingCanceled is called from IDEEventQueue.EditingCanceller when a child component
  // of a JTable receives a KeyEvent for the VK_ESCAPE key.
  // However we do NOT want to stop editing the cell if our editor currently is showing completion
  // results. The completion lookup is supposed to consume the key event but it cannot do that here
  // because of the preprocessing performed in IDEEventQueue.
  @Override
  @SuppressWarnings("deprecation")  // For CompletionProgressIndicator
  public void editingCanceled(@Nullable ChangeEvent event) {
    CompletionProcess process = CompletionService.getCompletionService().getCurrentCompletion();
    if (process instanceof CompletionProgressIndicator) {
      Hint hint = ((CompletionProgressIndicator)process).getLookup();
      if (hint != null) {
        hint.hide();
        return;
      }
    }
    super.editingCanceled(event);
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    PTableItem value = (PTableItem)getValueAt(row, column);
    if (column == 0) {
      return myRendererProvider.getNameCellRenderer(value);
    }
    else {
      TableCellRenderer renderer = value.getCellRenderer();
      if (renderer != null) {
        return renderer;
      }
      return myRendererProvider.getValueCellRenderer(value);
    }
  }

  @Override
  public PTableCellEditor getCellEditor(int row, int column) {
    PTableItem value = (PTableItem)getValueAt(row, column);
    if (value != null && myEditorProvider != null) {
      return myEditorProvider.getCellEditor(value, column);
    }
    return null;
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

    // Page Up & Page Down
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "pageUp");
    actionMap.put("pageUp", new MyPageUpAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "pageDown");
    actionMap.put("pageDown", new MyPageDownAction());
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
      startEditing(row, null);
    }
  }

  private void startEditing(int row, @Nullable Runnable afterActivation) {
    PTableCellEditor editor = getCellEditor(row, 0);
    if (editor == null) {
      return;
    }

    PTableItem item = (PTableItem)getValueAt(row, 0);
    if (item == null || !editCellAt(row, item.getColumnToEdit())) {
      return;
    }

    JComponent preferredComponent = getComponentToFocus(editor);
    if (preferredComponent == null) {
      return;
    }

    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      preferredComponent.requestFocusInWindow();
      editor.activate();
      if (afterActivation != null) {
        afterActivation.run();
      }
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
        startEditing(selectedRow, null);
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

  /**
   * Scroll the selected row when pressing Page Up key
   */
  private class MyPageUpAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      // PTable may in a scrollable component, so we need to use visible height instead of getHeight();
      int visibleHeight = (int) (getVisibleRect().getHeight());
      int rowHeight = getRowHeight();
      if (visibleHeight <= 0 || rowHeight <= 0) {
        return;
      }
      int movement = visibleHeight / rowHeight;
      selectRow(Math.max(0, selectedRow - movement));
    }
  }

  /**
   * Scroll the selected row when pressing Page Down key
   */
  private class MyPageDownAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      // PTable may in a scrollable component, so we need to use visible height instead of getHeight();
      int visibleHeight = (int) (getVisibleRect().getHeight());
      int rowHeight = getRowHeight();
      if (visibleHeight <= 0 || rowHeight <= 0) {
        return;
      }
      int movement = visibleHeight / rowHeight;
      selectRow(Math.min(selectedRow + movement, getRowCount() - 1));
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
        int x = e.getX() - rectLeftColumn.x;
        int y = e.getY() - rectLeftColumn.y;
        PNameRenderer nameRenderer = myRendererProvider.getNameCellRenderer(item);

        if (nameRenderer.hitTestTreeNodeIcon(item, x, y) && item.hasChildren()) {
          toggleTreeNode(row);
          return;
        }
        if (nameRenderer.hitTestStarIcon(x, y)) {
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

  /**
   * PTableKeyListener is our own implementation of "JTable.autoStartsEdit"
   */
  private static class PTableKeyListener extends KeyAdapter {
    private final PTable myTable;

    private PTableKeyListener(@NotNull PTable table) {
      myTable = table;
    }

    @Override
    public void keyTyped(@NotNull KeyEvent event) {
      int row = myTable.getSelectedRow();
      if (myTable.isEditing() || row == -1) {
        return;
      }
      myTable.startEditing(row, () ->
        ApplicationManager.getApplication().invokeLater(() -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          Component textEditor = IdeFocusManager.findInstance().getFocusOwner();
          if (!(textEditor instanceof JTextComponent)) {
            return;
          }
          KeyEvent keyEvent =
            new KeyEvent(textEditor, event.getID(), event.getWhen(), event.getModifiers(), event.getKeyCode(), event.getKeyChar());
          textEditor.dispatchEvent(keyEvent);
        })));
    }
  }
}
