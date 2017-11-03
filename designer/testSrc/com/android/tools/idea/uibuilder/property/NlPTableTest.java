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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.adtui.ptable.simple.SimpleItem;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class NlPTableTest extends AndroidTestCase {
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
    NlSimpleGroupItem groupItem = new NlSimpleGroupItem("group", ImmutableList.of(myItem1, myItem2, myItem3));
    PTableModel model = new PTableModel();
    model.setItems(ImmutableList.of(mySimpleItem, myEmptyItem, groupItem));
    myTable = new NlPTable(model, myCopyPasteManager);
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

  private class NlSimpleGroupItem extends NlPTableGroupItem {
    private final String myName;

    public NlSimpleGroupItem(@NotNull String name, @NotNull List<PTableItem> children) {
      myName = name;
      setChildren(children);
    }

    @NotNull
    @Override
    public String getChildLabel(@NotNull PTableItem item) {
      return myName + "." + item.getName();
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }
  }
}