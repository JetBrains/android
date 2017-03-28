/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class PTableTest extends AndroidTestCase {
  @Mock
  private DataContext myContext;
  @Mock
  private CopyPasteManager myCopyPasteManager;

  private SimpleItem mySimpleItem;
  private SimpleItem myEmptyItem;
  private SimpleItem myItem1;
  private SimpleItem myItem2;
  private SimpleItem myItem3;
  private PTable myTable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    PsiFile xmlFile = myFixture.configureByText("res/layout/layout.xml", "<LinearLayout/>");
    when(myContext.getData(CommonDataKeys.PROJECT.getName())).thenReturn(getProject());
    when(myContext.getData(CommonDataKeys.VIRTUAL_FILE.getName())).thenReturn(xmlFile.getVirtualFile());

    mySimpleItem = new SimpleItem("simple", "value");
    myEmptyItem = new SimpleItem("empty", null);
    myItem1 = new SimpleItem("other1", "other");
    myItem2 = new SimpleItem("other2", null);
    myItem3 = new SimpleItem("other3", "something");
    GroupItem groupItem = new GroupItem("group", ImmutableList.of(myItem1, myItem2, myItem3));
    PTableModel model = new PTableModel();
    model.setItems(ImmutableList.of(mySimpleItem, myEmptyItem, groupItem));
    myTable = new PTable(model, myCopyPasteManager);
  }

  public void testCopyIsNotAvailableWhenNothingIsSelected() {
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isFalse();
    myTable.performCopy(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  public void testCopyWithSimpleRowSelected() throws Exception {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isTrue();
    myTable.performCopy(myContext);
    assertHasClipboardValue("value");
    assertHasOriginalValues();
  }

  public void testCopyRowWithEmptyValueSelected() throws Exception {
    myTable.setRowSelectionInterval(1, 1);
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isTrue();
    myTable.performCopy(myContext);
    assertHasClipboardValue(null);
    assertHasOriginalValues();
  }

  public void testCopyIsNotAvailableFromGroupNode() throws Exception {
    myTable.setRowSelectionInterval(2, 2);
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isFalse();
    myTable.performCopy(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  public void testPasteIsNotAvailableWhenNothingIsSelected() {
    when(myCopyPasteManager.getContents()).thenReturn(new StringSelection("new value"));
    assertThat(myTable.isPastePossible(myContext)).isFalse();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertHasOriginalValues();
  }

  public void testPasteIsNotAvailableWhenNothingIsOnTheClipboard() {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isPastePossible(myContext)).isFalse();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertHasOriginalValues();
  }

  public void testPasteIntoSimpleItem() {
    when(myCopyPasteManager.getContents()).thenReturn(new StringSelection("new value"));
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isPastePossible(myContext)).isTrue();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertThat(mySimpleItem.getValue()).isEqualTo("new value");
    assertHasOriginalValuesExceptFor(mySimpleItem);
  }

  public void testPasteIsNotAvailableToGroupNode() throws Exception {
    when(myCopyPasteManager.getContents()).thenReturn(new StringSelection("new value"));
    myTable.setRowSelectionInterval(2, 2);
    assertThat(myTable.isPastePossible(myContext)).isFalse();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertHasOriginalValues();
  }

  public void testCutIsNotAvailableWhenNothingIsSelected() {
    assertThat(myTable.isCutVisible(myContext)).isTrue();
    assertThat(myTable.isCutEnabled(myContext)).isFalse();
    myTable.performCut(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  public void testCutFromSimpleItem() throws Exception {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isCutVisible(myContext)).isTrue();
    assertThat(myTable.isCutEnabled(myContext)).isTrue();
    myTable.performCut(myContext);
    assertThat(mySimpleItem.getValue()).isNull();
    assertHasClipboardValue("value");
    assertHasOriginalValuesExceptFor(mySimpleItem);
  }

  public void testCutIsNotAvailableWhenGroupIsSelected() {
    myTable.setRowSelectionInterval(2, 2);
    assertThat(myTable.isCutVisible(myContext)).isTrue();
    assertThat(myTable.isCutEnabled(myContext)).isFalse();
    myTable.performCut(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  public void testDeleteIsNotAvailableWhenNothingIsSelected() {
    assertThat(myTable.canDeleteElement(myContext)).isFalse();
    myTable.deleteElement(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  public void testDeleteOfSimpleItem() {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.canDeleteElement(myContext)).isTrue();
    myTable.deleteElement(myContext);
    assertThat(mySimpleItem.getValue()).isNull();
    assertHasEmptyClipboard();
    assertHasOriginalValuesExceptFor(mySimpleItem);
  }

  public void testDeleteOfGroupItem() {
    myTable.setRowSelectionInterval(2, 2);
    assertThat(myTable.canDeleteElement(myContext)).isTrue();
    myTable.deleteElement(myContext);
    assertThat(myItem1.getValue()).isNull();
    assertThat(myItem2.getValue()).isNull();
    assertThat(myItem3.getValue()).isNull();
    assertHasEmptyClipboard();
    assertHasOriginalValuesExceptFor(myItem1, myItem2, myItem3);
  }

  public void testClickOnStar() {
    fireMousePressed(1, 0, 10);
    assertThat(myEmptyItem.getStarState()).isEqualTo(StarState.STARRED);
    fireMousePressed(1, 0, 10);
    assertThat(myEmptyItem.getStarState()).isEqualTo(StarState.STAR_ABLE);
  }

  public void testRestoreSelection() {
    assertThat(myTable.getSelectedItem()).isNull();
    myTable.restoreSelection(1, myEmptyItem);
    assertThat(myTable.getSelectedItem()).isSameAs(myEmptyItem);
  }

  public void testRestoreSelectionWhereRowDoesNotMatch() {
    assertThat(myTable.getSelectedItem()).isNull();
    myTable.restoreSelection(77, new SimpleItem("empty", null));
    assertThat(myTable.getSelectedItem()).isSameAs(myEmptyItem);
  }

  private void fireMousePressed(int row, int column, int xOffset) {
    Rectangle bounds = myTable.getCellRect(row, column, false);
    int x = bounds.x + xOffset;
    int y = bounds.y + 5;
    MouseEvent event = mock(MouseEvent.class);
    when(event.getX()).thenReturn(x);
    when(event.getY()).thenReturn(y);
    when(event.getPoint()).thenReturn(new Point(x, y));
    when(event.getComponent()).thenReturn(myTable);

    for (MouseListener listener : myTable.getMouseListeners()) {
      listener.mousePressed(event);
    }
  }

  private void assertHasOriginalValues() {
    assertThat(mySimpleItem.getValue()).isEqualTo("value");
    assertThat(myEmptyItem.getValue()).isNull();
    assertThat(myItem1.getValue()).isEqualTo("other");
    assertThat(myItem2.getValue()).isNull();
    assertThat(myItem3.getValue()).isEqualTo("something");
  }

  private void assertHasOriginalValuesExceptFor(@NotNull SimpleItem... exceptions) {
    Set<SimpleItem> items = new HashSet<>(ImmutableList.of(mySimpleItem, myEmptyItem, myItem1, myItem2, myItem3));
    items.removeAll(Arrays.asList(exceptions));
    for (SimpleItem item : items) {
      switch (item.getName()) {
        case "simple":
          assertThat(mySimpleItem.getValue()).isEqualTo("value");
          break;
        case "empty":
          assertThat(myEmptyItem.getValue()).isNull();
          break;
        case "item1":
          assertThat(myItem1.getValue()).isEqualTo("other");
          break;
        case "item2":
          assertThat(myItem2.getValue()).isNull();
          break;
        case "item3":
          assertThat(myItem3.getValue()).isEqualTo("something");
          break;
      }
    }
  }

  private void assertHasEmptyClipboard() {
    verifyZeroInteractions(myCopyPasteManager);
  }

  private void assertHasClipboardValue(@Nullable String value) throws Exception {
    ArgumentCaptor<Transferable> captor = ArgumentCaptor.forClass(Transferable.class);
    verify(myCopyPasteManager).setContents(captor.capture());
    Transferable transferable = captor.getValue();
    assertThat(transferable).isNotNull();
    assertThat(transferable.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue();
    assertThat(transferable.getTransferData(DataFlavor.stringFlavor)).isEqualTo(value);
  }

  private static class SimpleItem extends PTableItem {
    private String myName;
    private String myValue;
    private StarState myStarState;

    private SimpleItem(@NotNull String name, @Nullable String value) {
      myName = name;
      myValue = value;
      myStarState = StarState.STAR_ABLE;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public String getValue() {
      return myValue;
    }

    @NotNull
    @Override
    public StarState getStarState() {
      return myStarState;
    }

    @Override
    public void setStarState(@NotNull StarState starState) {
      myStarState = starState;
    }

    @Nullable
    @Override
    public String getResolvedValue() {
      return myValue;
    }

    @Override
    public boolean isDefaultValue(@Nullable String value) {
      return value == null;
    }

    @Override
    public void setValue(@Nullable Object value) {
      myValue = (String)value;
    }
  }

  private static class GroupItem extends PTableGroupItem {
    private String myName;

    private GroupItem(@NotNull String name, @NotNull List<PTableItem> children) {
      myName = name;
      setChildren(children);
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }
  }
}
