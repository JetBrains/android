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
package com.android.tools.profilers.network;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

public class ConnectionsViewTest {
  public static final ImmutableList<HttpData> FAKE_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(FakeNetworkService.newHttpData(1, 1, 1, 2))
      .add(FakeNetworkService.newHttpData(2, 3, 4, 5))
      .add(FakeNetworkService.newHttpData(3, 8, 10, 13))
      .add(FakeNetworkService.newHttpData(4, 21, 25, 34))
      .build();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("ConnectionsViewTest", new FakeProfilerService(false),
                                                                   FakeNetworkService.newBuilder().setHttpDataList(FAKE_DATA).build());
  private NetworkProfilerStageView myStageView;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer());
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    myStageView = new NetworkProfilerStageView(profilersView, new NetworkProfilerStage(profilers));
  }

  @Test
  public void logicToExtractColumnValuesFromDataWorks() throws Exception {
    HttpData data = FAKE_DATA.get(2); // Request: id = 3, time = 8->13
    assertThat(ConnectionsView.Column.NAME.getValueFrom(data), is("3"));
    assertThat(ConnectionsView.Column.SIZE.getValueFrom(data), is(3));
    assertThat(ConnectionsView.Column.TYPE.getValueFrom(data), is("image/jpeg"));
    assertThat(ConnectionsView.Column.STATUS.getValueFrom(data), is(302));
    assertThat(ConnectionsView.Column.TIME.getValueFrom(data), is(TimeUnit.SECONDS.toMicros(5)));
    assertThat(ConnectionsView.Column.TIMELINE.getValueFrom(data), is(TimeUnit.SECONDS.toMicros(8)));
  }

  @Test
  public void dataRangeControlsVisibleConnections() throws Exception {
    ConnectionsView view = new ConnectionsView(myStageView);
    JTable table = getConnectionsTable(view);

    assertThat(table.getRowCount(), is(0));

    myStageView.getTimeline().getSelectionRange().set(TimeUnit.SECONDS.toMicros(3), TimeUnit.SECONDS.toMicros(10));
    assertThat(table.getRowCount(), is(2));

    myStageView.getTimeline().getSelectionRange().set(0, 0);
    assertThat(table.getRowCount(), is(0));
  }

  @Test
  public void activeConnectionIsAutoFocusedByTable() throws Exception {
    ConnectionsView view = new ConnectionsView(myStageView);

    JTable table = getConnectionsTable(view);
    final int[] selectedRow = {-1};

    // We arbitrarily select one of the fake data instances and sanity check that the table
    // auto-selects it, which is checked for below.
    int arbitraryIndex = 1;
    HttpData activeData = FAKE_DATA.get(arbitraryIndex);
    myStageView.getStage().setSelectedConnection(activeData);

    CountDownLatch latchSelected = new CountDownLatch(1);
    table.getSelectionModel().addListSelectionListener(e -> {
      selectedRow[0] = e.getFirstIndex();
      latchSelected.countDown();
    });

    myStageView.getTimeline().getSelectionRange().set(0, TimeUnit.SECONDS.toMicros(100));
    latchSelected.await();
    assertThat(selectedRow[0], is(arbitraryIndex));
  }

  @Test
  public void tableCanBeSorted() throws Exception {
    myStageView.getTimeline().getSelectionRange().set(0, TimeUnit.SECONDS.toMicros(100));
    ConnectionsView view = new ConnectionsView(myStageView);

    JTable table = getConnectionsTable(view);

    // Times: 1, 2, 5, 13. Should sort numerically, not alphabetically (e.g. not 1, 13, 2, 5)
    // Toggle once for ascending, twice for descending
    table.getRowSorter().toggleSortOrder(ConnectionsView.Column.TIME.ordinal());
    table.getRowSorter().toggleSortOrder(ConnectionsView.Column.TIME.ordinal());

    // After reverse sorting, data should be backwards
    assertThat(table.getRowCount(), is(4));
    assertThat(table.convertRowIndexToView(0), is(3));
    assertThat(table.convertRowIndexToView(1), is(2));
    assertThat(table.convertRowIndexToView(2), is(1));
    assertThat(table.convertRowIndexToView(3), is(0));

    myStageView.getTimeline().getSelectionRange().set(0, 0);
    assertThat(table.getRowCount(), is(0));

    // Include middle two requests: 3->5 (time = 2), and 8->13 (time=5)
    // This should still be shown in reverse sorted over
    myStageView.getTimeline().getSelectionRange().set(TimeUnit.SECONDS.toMicros(3), TimeUnit.SECONDS.toMicros(10));
    assertThat(table.getRowCount(), is(2));
    assertThat(table.convertRowIndexToView(0), is(1));
    assertThat(table.convertRowIndexToView(1), is(0));
  }

  @Test
  public void testTableRowHighlight() {
    myStageView.getTimeline().getSelectionRange().set(0, TimeUnit.SECONDS.toMicros(100));
    ConnectionsView view = new ConnectionsView(myStageView);
    int timelineColumn = ConnectionsView.Column.TIMELINE.ordinal();
    JTable table = getConnectionsTable(view);

    Color backgroundColor = Color.YELLOW;
    Color selectionColor = Color.BLUE;
    table.setBackground(backgroundColor);
    table.setSelectionBackground(selectionColor);

    TableCellRenderer renderer = table.getCellRenderer(1, timelineColumn);
    assertThat(table.prepareRenderer(renderer, 1, timelineColumn).getBackground(), is(backgroundColor));

    table.setRowSelectionInterval(1, 1);
    assertThat(table.prepareRenderer(renderer, 1, timelineColumn).getBackground(), is(selectionColor));
  }

  @Test
  public void ensureAxisInTheFirstRow() throws Exception {
    myStageView.getTimeline().getSelectionRange().set(0, TimeUnit.SECONDS.toMicros(100));
    ConnectionsView view = new ConnectionsView(myStageView);

    JTable table = getConnectionsTable(view);

    int timelineColumn = ConnectionsView.Column.TIMELINE.ordinal();
    TableCellRenderer renderer = table.getCellRenderer(1, timelineColumn);

    Component comp = table.prepareRenderer(renderer, 0, timelineColumn);
    assertThat(comp, instanceOf(JPanel.class));
    assertThat(((JPanel)comp).getComponent(0), instanceOf(AxisComponent.class));
    assertThat(((JPanel)comp).getComponent(1), instanceOf(StateChart.class));
  }

  /**
   * The underlying table in ConnectionsView is intentionally not exposed to regular users of the
   * class. However, for tests, it is useful to inspect the contents of the table to verify it was
   * updated.
   */
  private static JTable getConnectionsTable(ConnectionsView view) {
    return (JTable)view.getComponent();
  }
}