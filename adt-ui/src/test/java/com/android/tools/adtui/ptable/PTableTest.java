/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.ptable;

import com.android.tools.adtui.ptable.simple.SimpleGroupItem;
import com.android.tools.adtui.ptable.simple.SimpleItem;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class PTableTest {
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

  @Before
  public void setUp() throws Exception {
    initMocks(this);

    mySimpleItem = new SimpleItem("simple", "value");
    myEmptyItem = new SimpleItem("empty", null);
    myItem1 = new SimpleItem("other1", "other");
    myItem2 = new SimpleItem("other2", null);
    myItem3 = new SimpleItem("other3", "something");
    SimpleGroupItem groupItem = new SimpleGroupItem("group", ImmutableList.of(myItem1, myItem2, myItem3));
    PTableModel model = new PTableModel();
    model.setItems(ImmutableList.of(mySimpleItem, myEmptyItem, groupItem));
    myTable = new PTable(model, myCopyPasteManager);
  }

  @Test
  public void testCopyIsNotAvailableWhenNothingIsSelected() {
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isFalse();
    myTable.performCopy(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  @Test
  public void testCopyWithSimpleRowSelected() throws Exception {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isTrue();
    myTable.performCopy(myContext);
    assertHasClipboardValue("value");
    assertHasOriginalValues();
  }

  @Test
  public void testCopyRowWithEmptyValueSelected() throws Exception {
    myTable.setRowSelectionInterval(1, 1);
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isTrue();
    myTable.performCopy(myContext);
    assertHasClipboardValue(null);
    assertHasOriginalValues();
  }

  @Test
  public void testCopyIsNotAvailableFromGroupNode() throws Exception {
    myTable.setRowSelectionInterval(2, 2);
    assertThat(myTable.isCopyVisible(myContext)).isTrue();
    assertThat(myTable.isCopyEnabled(myContext)).isFalse();
    myTable.performCopy(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  @Test
  public void testPasteIsNotAvailableWhenNothingIsSelected() {
    when(myCopyPasteManager.getContents()).thenReturn(new StringSelection("new value"));
    assertThat(myTable.isPastePossible(myContext)).isFalse();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertHasOriginalValues();
  }

  @Test
  public void testPasteIsNotAvailableWhenNothingIsOnTheClipboard() {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isPastePossible(myContext)).isFalse();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertHasOriginalValues();
  }

  @Test
  public void testPasteIntoSimpleItem() {
    when(myCopyPasteManager.getContents()).thenReturn(new StringSelection("new value"));
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isPastePossible(myContext)).isTrue();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertThat(mySimpleItem.getValue()).isEqualTo("new value");
    assertHasOriginalValuesExceptFor(mySimpleItem);
  }

  @Test
  public void testPasteIsNotAvailableToGroupNode() throws Exception {
    when(myCopyPasteManager.getContents()).thenReturn(new StringSelection("new value"));
    myTable.setRowSelectionInterval(2, 2);
    assertThat(myTable.isPastePossible(myContext)).isFalse();
    assertThat(myTable.isPasteEnabled(myContext)).isTrue();
    myTable.performPaste(myContext);
    assertHasOriginalValues();
  }

  @Test
  public void testCutIsNotAvailableWhenNothingIsSelected() {
    assertThat(myTable.isCutVisible(myContext)).isTrue();
    assertThat(myTable.isCutEnabled(myContext)).isFalse();
    myTable.performCut(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  @Test
  public void testCutFromSimpleItem() throws Exception {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.isCutVisible(myContext)).isTrue();
    assertThat(myTable.isCutEnabled(myContext)).isTrue();
    myTable.performCut(myContext);
    assertThat(mySimpleItem.getValue()).isNull();
    assertHasClipboardValue("value");
    assertHasOriginalValuesExceptFor(mySimpleItem);
  }

  @Test
  public void testCutIsNotAvailableWhenGroupIsSelected() {
    myTable.setRowSelectionInterval(2, 2);
    assertThat(myTable.isCutVisible(myContext)).isTrue();
    assertThat(myTable.isCutEnabled(myContext)).isFalse();
    myTable.performCut(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  @Test
  public void testDeleteIsNotAvailableWhenNothingIsSelected() {
    assertThat(myTable.canDeleteElement(myContext)).isFalse();
    myTable.deleteElement(myContext);
    assertHasEmptyClipboard();
    assertHasOriginalValues();
  }

  @Test
  public void testDeleteOfSimpleItem() {
    myTable.setRowSelectionInterval(0, 0);
    assertThat(myTable.canDeleteElement(myContext)).isTrue();
    myTable.deleteElement(myContext);
    assertThat(mySimpleItem.getValue()).isNull();
    assertHasEmptyClipboard();
    assertHasOriginalValuesExceptFor(mySimpleItem);
  }

  @Test
  public void testRestoreSelection() {
    assertThat(myTable.getSelectedItem()).isNull();
    myTable.restoreSelection(1, myEmptyItem);
    assertThat(myTable.getSelectedItem()).isSameAs(myEmptyItem);
  }

  @Test
  public void testRestoreSelectionWhereRowDoesNotMatch() {
    assertThat(myTable.getSelectedItem()).isNull();
    myTable.restoreSelection(77, new SimpleItem("empty", null));
    assertThat(myTable.getSelectedItem()).isSameAs(myEmptyItem);
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
}
