/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.intellij.openapi.util.IconLoader;
import java.awt.Component;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ItemListTest {
  private DependencyManager myDependencyManager;
  private ItemList myItemList;
  private Palette.Item myItem1;
  private Palette.Item myItem2;

  @Before
  public void setUp() {
    myDependencyManager = mock(DependencyManager.class);
    myItemList = new ItemList(myDependencyManager);
    myItem1 = new Palette.Item("Tag1", new ViewHandler());
    myItem2 = new Palette.Item("Tag2", new ViewHandler());
    when(myDependencyManager.needsLibraryLoad(eq(myItem1))).thenReturn(false);
    when(myDependencyManager.needsLibraryLoad(eq(myItem2))).thenReturn(true);
  }

  @After
  public void tearDown() {
    myDependencyManager = null;
    myItemList = null;
    myItem1 = null;
    myItem2 = null;
  }

  @Test
  public void testToolTipForDownloadIcon() {
    // TODO: With GhostAwt it might be possible to call myItemList.getToolTipText(event) directly.
    // Without GhostAwt the UI classes causes trouble in unit tests.
    IconLoader.activate();
    myItemList.setSize(100, 300);
    assertThat(getRendererFor(0, myItem1).getToolTipText(eventAt(95, 10))).isNull();
    assertThat(getRendererFor(1, myItem2).getToolTipText(eventAt(95, 10))).isEqualTo("Add Project Dependency");
  }

  @NotNull
  private MouseEvent eventAt(int x, int y) {
    return new MouseEvent(myItemList, 0, 0L, 0, x, y, 0, false);
  }

  @NotNull
  private JComponent getRendererFor(int index, Palette.Item item) {
    return (JComponent)myItemList.getCellRenderer().getListCellRendererComponent(myItemList, item, index, false, false);
  }
}
