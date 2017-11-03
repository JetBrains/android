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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.adtui.ptable.*;
import com.android.tools.adtui.ptable.simple.SimpleGroupItem;
import com.android.tools.adtui.ptable.simple.SimpleItem;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class NlTableNameRendererTest extends AndroidTestCase {
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

  public void testClickOnStar() {
    NlTableNameRenderer renderer = new NlTableNameRenderer();
    myTable.setRendererProvider(new PTableCellRendererProvider() {
      @NotNull
      @Override
      public PNameRenderer getNameCellRenderer(@NotNull PTableItem item) {
        return renderer;
      }

      @NotNull
      @Override
      public TableCellRenderer getValueCellRenderer(@NotNull PTableItem item) {
        return renderer;
      }
    });
    fireMousePressed(1, 0, 10);
    assertThat(myEmptyItem.getStarState()).isEqualTo(StarState.STARRED);
    fireMousePressed(1, 0, 10);
    assertThat(myEmptyItem.getStarState()).isEqualTo(StarState.STAR_ABLE);
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
}
