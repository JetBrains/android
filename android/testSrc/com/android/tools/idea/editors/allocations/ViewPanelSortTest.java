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
package com.android.tools.idea.editors.allocations;

import com.android.ddmlib.AllocationInfo;
import com.android.ddmlib.AllocationsParser;
import com.android.ddmlib.allocations.AllocationsParserTest;
import com.android.tools.idea.editors.allocations.AllocationsTableUtil.Column;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.JBTable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.intellij.util.config.Storage;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.*;

@RunWith(Parameterized.class)
public class ViewPanelSortTest {
  private static final AllocationInfo.AllocationSorter SORTER = new AllocationInfo.AllocationSorter();
  private static final String[][] HEADERS = new String[][]{
    {"Green", "128", "2"}, {"Red[]", "42", "3"}, {"Blue", "16", "2"}, {"Red[]", "1024", "1"}
  };
  private static final int COLUMN_COUNT = 5;

  private static AllocationInfo[] sAllocations;
  private static JBCheckBox sGroupingCheckBox;
  private static JBTable sAllocationsTable;

  private int myColumn;
  private boolean myDescending;

  public ViewPanelSortTest(int column, boolean descending) {
    myColumn = column;
    myDescending = descending;
    sAllocationsTable.clearSelection();
  }

  @Parameterized.Parameters(name = "{index}: ({0}, {1})")
  public static Collection<Object[]> arguments() {
    ArrayList<Object[]> list = new ArrayList<Object[]>();
    for (int i = 0; i < COLUMN_COUNT; ++i) {
      list.add(new Object[]{i, false});
      list.add(new Object[]{i, true});
    }
    return list;
  }

  @Test
  public void testUnGroupedView() {
    setUpUnGroupedView();
    toggleCurrentColumn();
    SORTER.setSortMode(columnToMode(myColumn), myDescending);
    Arrays.sort(sAllocations, SORTER);
    checkUnGroupedView();
  }

  private static void setUpUnGroupedView() {
    sAllocationsTable.getRowSorter().modelStructureChanged();
    sGroupingCheckBox.setSelected(false);
    assertEquals(sAllocations.length, sAllocationsTable.getRowCount());
    assertEquals(COLUMN_COUNT, sAllocationsTable.getColumnCount());
    SORTER.setSortMode(AllocationInfo.SortMode.NUMBER, true);
    Arrays.sort(sAllocations, SORTER);
    checkUnGroupedView();
  }

  private void toggleCurrentColumn() {
    sAllocationsTable.getRowSorter().toggleSortOrder(myColumn);
    if (myDescending) {
      sAllocationsTable.getRowSorter().toggleSortOrder(myColumn);
    }
  }

  private static void checkUnGroupedView() {
    for (int i = 0; i < sAllocations.length; ++i) {
      checkRow(sAllocations[i], i);
    }
  }

  private static void checkRow(AllocationInfo allocation, int row) {
    for (int i = 0; i < COLUMN_COUNT; ++i) {
      Object value;
      switch (Column.values()[i]) {
        case ALLOCATION_ORDER:
          value = allocation.getAllocNumber();
          break;
        case ALLOCATED_CLASS:
          value = allocation.getAllocatedClass();
          break;
        case ALLOCATION_SIZE:
          value = allocation.getSize();
          break;
        case THREAD_ID:
          value = allocation.getThreadId();
          break;
        case ALLOCATION_SITE:
          value = allocation.getAllocationSite();
          break;
        default:
          value = null;
      }
      assertEquals(value, sAllocationsTable.getValueAt(row, i));
    }
  }

  private static AllocationInfo.SortMode columnToMode(int column) {
    switch (Column.values()[column]) {
      case ALLOCATION_ORDER:
        return AllocationInfo.SortMode.NUMBER;
      case ALLOCATED_CLASS:
        return AllocationInfo.SortMode.CLASS;
      case ALLOCATION_SIZE:
        return AllocationInfo.SortMode.SIZE;
      case THREAD_ID:
        return AllocationInfo.SortMode.THREAD;
      case ALLOCATION_SITE:
        return AllocationInfo.SortMode.ALLOCATION_SITE;
      default:
        throw new IllegalArgumentException();
    }
  }

  @BeforeClass
  public static void oneTimeSetUp() throws Exception {
    ByteBuffer data = AllocationsParserTest.putAllocationInfo(new String[]{HEADERS[0][0], HEADERS[1][0], HEADERS[2][0], HEADERS[3][0]},
            new String[]{"eatTiramisu", "failUnitTest", "watchCatVideos", "passGo", "collectFakeMoney", "findWaldo"},
            new String[]{"Red.java", "SomewhatBlue.java", "LightCanaryishGrey.java"},
            new int[][]{
              {Integer.parseInt(HEADERS[0][1]), 8, 0, 2}, {Integer.parseInt(HEADERS[1][1]), 4, 1, 1},
              {Integer.parseInt(HEADERS[0][1]), 8, 0, 1}, {Integer.parseInt(HEADERS[3][1]), 4, 3, 1},
              {Integer.parseInt(HEADERS[1][1]), 8, 1, 2}, {Integer.parseInt(HEADERS[2][1]), 4, 2, 2},
              {Integer.parseInt(HEADERS[2][1]), 8, 2, 1}, {Integer.parseInt(HEADERS[1][1]), 4, 1, 1}},
            new short[][][]{{{1, 0, 1, 100}, {2, 5, 1, -2}}, {{0, 1, 0, -1}},
              {{3, 4, 2, 10001}}, {{0, 3, 0, 0}}, {{2, 2, 1, 16}, {3, 4, 2, 10}},
              {{0, 3, 0, -2}, {2, 5, 1, 1000}}, {{1, 0, 1, 50}}, {{2, 2, 1, 666}}});

    sAllocations = AllocationsParser.parse(data);
    AllocationsViewPanel panel = getPanel();
    panel.setAllocations(sAllocations.clone());

    Stack<Container> containers = new Stack<Container>();
    containers.add(panel.getComponent());
    while (!containers.isEmpty() && (sGroupingCheckBox == null || sAllocationsTable == null)) {
      for (Component component : containers.pop().getComponents()) {
        if (component.getName() != null && component.getName().equals(AllocationsViewPanel.GROUPING_CHECKBOX_NAME)) {
          sGroupingCheckBox = (JBCheckBox) component;
        } else if (component.getName() != null && component.getName().equals(AllocationsViewPanel.ALLOCATIONS_TABLE_NAME)) {
          sAllocationsTable = (JBTable) component;
        } else if (component instanceof Container) {
          containers.add((Container) component);
        }
      }
    }
    assertNotNull(sGroupingCheckBox);
    assertNotNull(sAllocationsTable);
  }

  @NotNull
  private static AllocationsViewPanel getPanel() {
    Project mockProject = EasyMock.createMock(Project.class);
    return new AllocationsViewPanel(mockProject) {
      @Override
      ConsoleView createConsoleView(@NotNull Project project) {
        return null;
      }

      @Override
      Storage.PropertiesComponentStorage getStorage() {
        return null;
      }
    };
  }
}
