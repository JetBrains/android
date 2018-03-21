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
package com.android.tools.profilers.network;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.laf.HeadlessTableUI;
import com.android.tools.profilers.*;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ThreadsViewTest {
  private static final ImmutableList<HttpData> FAKE_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(newData(1, 1, 10, 11, "threadA"))
      .add(newData(2, 5, 12, 12, "threadB"))
      .add(newData(3, 13, 15, 11, "threadA"))
      .add(newData(4, 20, 25, 11, "threadA"))
      .add(newData(5, 14, 21, 12, "threadB"))

      .add(newData(11, 100, 110, 13, "threadC"))
      .add(newData(12, 115, 120, 14, "threadC"))
      .build();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("ThreadsViewTest", new FakeProfilerService(false),
                                                                   FakeNetworkService.newBuilder().setHttpDataList(FAKE_DATA).build());
  private NetworkProfilerStageView myStageView;
  private ThreadsView myThreadsView;
  private FakeUi myUi;

  @Before
  public void setUp() throws InvocationTargetException, InterruptedException {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer());
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    myStageView = new NetworkProfilerStageView(profilersView, new NetworkProfilerStage(profilers));
    myThreadsView = new ThreadsView(myStageView);
    myThreadsView.getComponent().setSize(new Dimension(300, 50));
    getTable().setUI(new HeadlessTableUI());
    // Normally, when ThreadsView changes size, it updates the size of its table which in turn
    // fires an event that updates the preferred size of its columns. This requires multiple layout
    // passes, as well as firing a event that happens on another thread, so the timing is not
    // deterministic. For testing, we short-circuit the process and set the size of the table
    // directly, so when the FakeUi is created below (which performs a layout pass), the table will
    // already be in its final size.
    JTable table = getTable();
    table.setSize(myThreadsView.getComponent().getSize());
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance()); // Allow table columns to resize

    myUi = new FakeUi(myThreadsView.getComponent());
  }

  @Test
  public void showsCorrectThreadData() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JTable table = getTable();

    selection.set(0, TimeUnit.SECONDS.toMicros(22));
    assertThat(table.getModel().getRowCount(), is(2));
    assertThat(table.getModel().getValueAt(0, 0), is("threadA"));
    assertThat(table.getModel().getValueAt(0, 1), is(Arrays.asList(FAKE_DATA.get(0), FAKE_DATA.get(2), FAKE_DATA.get(3))));
    assertThat(table.getModel().getValueAt(1, 0), is("threadB"));
    assertThat(table.getModel().getValueAt(1, 1), is(Arrays.asList(FAKE_DATA.get(1), FAKE_DATA.get(4))));
  }

  @Test
  public void shouldHandleEmptySelection() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JTable list = getTable();

    selection.set(0, TimeUnit.SECONDS.toMicros(22));
    assertThat(list.getModel().getRowCount(), is(2));

    selection.clear();
    assertThat(list.getModel().getRowCount(), is(0));
  }

  @Test
  public void shouldHandleThreadsWithTheSameNameButDifferentID() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JTable table = getTable();

    selection.set(TimeUnit.SECONDS.toMicros(99), TimeUnit.SECONDS.toMicros(120));
    assertThat(table.getModel().getRowCount(), is(2));
    assertThat(table.getModel().getValueAt(0, 0), is("threadC"));
    assertThat(table.getModel().getValueAt(0, 1), is(Collections.singletonList(FAKE_DATA.get(5))));
    assertThat(table.getModel().getValueAt(1, 0), is("threadC"));
    assertThat(table.getModel().getValueAt(1, 1), is(Collections.singletonList(FAKE_DATA.get(6))));
  }

  @Test
  public void tableCanBeSortedByInitiatingThreadColumn() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JTable table = getTable();

    selection.set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(200));

    table.getRowSorter().toggleSortOrder(table.getColumn("Initiating thread").getModelIndex());
    assertThat(table.getValueAt(0, 0), is("threadA"));
    assertThat(table.getValueAt(1, 0), is("threadB"));
    assertThat(table.getValueAt(2, 0), is("threadC"));
    assertThat(table.getValueAt(3, 0), is("threadC"));

    table.getRowSorter().toggleSortOrder(table.getColumn("Initiating thread").getModelIndex());
    assertThat(table.getValueAt(0, 0), is("threadC"));
    assertThat(table.getValueAt(1, 0), is("threadC"));
    assertThat(table.getValueAt(2, 0), is("threadB"));
    assertThat(table.getValueAt(3, 0), is("threadA"));
  }

  @Test
  public void tableCanBeSortedByTimelineColumn() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JTable table = getTable();

    selection.set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(200));

    table.getRowSorter().toggleSortOrder(table.getColumn("Timeline").getModelIndex());
    assertThat(getFirstHttpDataAtRow(table, 0).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(1)));
    assertThat(getFirstHttpDataAtRow(table, 1).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(5)));
    assertThat(getFirstHttpDataAtRow(table, 2).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(100)));
    assertThat(getFirstHttpDataAtRow(table, 3).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(115)));

    table.getRowSorter().toggleSortOrder(table.getColumn("Timeline").getModelIndex());
    assertThat(getFirstHttpDataAtRow(table, 0).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(115)));
    assertThat(getFirstHttpDataAtRow(table, 1).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(100)));
    assertThat(getFirstHttpDataAtRow(table, 2).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(5)));
    assertThat(getFirstHttpDataAtRow(table, 3).getStartTimeUs(), is(TimeUnit.SECONDS.toMicros(1)));
  }

  private static HttpData getFirstHttpDataAtRow(JTable table, int row) {
    List<HttpData> data = (List<HttpData>)table.getValueAt(row, 1);
    return data.get(0);
  }

  @Test
  public void ensureAxisInList() {
    JTable table = getTable();
    Range selection = myStageView.getTimeline().getSelectionRange();
    selection.set(0, TimeUnit.SECONDS.toMicros(22));

    TableCellRenderer renderer = table.getCellRenderer(0, 1);
    Component comp = table.prepareRenderer(renderer, 0, 1);
    assertTrue(new TreeWalker(comp).descendantStream().anyMatch(c -> c instanceof AxisComponent));
  }

  @Test
  public void clickingOnARequestSelectsIt() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    // The following selection puts threads in the first and second rows on the left
    // half of the view. The right half is mostly blank.
    selection.set(0, TimeUnit.SECONDS.toMicros(44));

    int badX = myThreadsView.getComponent().getWidth() - 1;
    int goodX = getTable().getColumnModel().getColumn(0).getWidth() + 10;
    int goodY = getTable().getRowHeight() / 2;

    assertNull(myStageView.getStage().getSelectedConnection());
    // Click on empty space - doesn't select anything
    myUi.mouse.click(badX, goodY);
    assertNull(myStageView.getStage().getSelectedConnection());

    myUi.mouse.click(goodX, goodY);
    assertNotNull(myStageView.getStage().getSelectedConnection());

    // After clicking on a request, clicking on empty space doesn't deselect
    myUi.mouse.click(badX, goodY);
    assertNotNull(myStageView.getStage().getSelectedConnection());
  }

  @NotNull
  private JTable getTable() {
    return (JTable)new TreeWalker(myThreadsView.getComponent()).descendantStream().filter(c -> c instanceof JTable).findFirst().get();
  }

  @NotNull
  private static HttpData newData(long id, long startS, long endS, long threadId, String threadName) {
    return TestHttpData.newBuilder(id, startS, endS, new HttpData.JavaThread(threadId, threadName)).build();
  }
}