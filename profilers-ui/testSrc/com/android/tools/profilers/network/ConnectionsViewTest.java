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
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.FakeTimer;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
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
  @Rule public TestGrpcChannel myGrpcChannel = new TestGrpcChannel<>("ConnectionsViewTest", new FakeNetworkService());
  private NetworkProfilerStage myStage;
  private FakeTimer myChoreographerTimer;
  private Choreographer myChoreographer;

  @Before
  public void setUp() {
    StudioProfilers profilers = myGrpcChannel.getProfilers();
    myStage = new NetworkProfilerStage(profilers);
    myChoreographerTimer = new FakeTimer();
    myChoreographer = new Choreographer(myChoreographerTimer);
  }

  @Test
  public void logicToExtractColumnValuesFromDataWorks() throws Exception {
    HttpData data = convert(FakeNetworkService.FAKE_DATA.get(2)); // Request: id = 3, time = 8->13
    assertThat(ConnectionsView.Column.URL.getValueFrom(data), is("http://example.com/3"));
    assertThat(ConnectionsView.Column.SIZE.getValueFrom(data), is(300));
    assertThat(ConnectionsView.Column.TYPE.getValueFrom(data), is("image/jpeg"));
    assertThat(ConnectionsView.Column.STATUS.getValueFrom(data), is(200));
    assertThat(ConnectionsView.Column.TIME.getValueFrom(data), is(TimeUnit.SECONDS.toMicros(5)));
    assertThat(ConnectionsView.Column.TIMELINE.getValueFrom(data), is(TimeUnit.SECONDS.toMicros(8)));
  }

  @Test
  public void dataRangeControlsVisibleConnections() throws Exception {
    Range dataRange = new Range();
    ConnectionsView view = new ConnectionsView(myStage, myChoreographer, dataRange, data -> {});
    JTable table = getConnectionsTable(view);

    myChoreographerTimer.step();
    assertThat(table.getRowCount(), is(0));

    dataRange.set(TimeUnit.SECONDS.toMicros(3), TimeUnit.SECONDS.toMicros(10));
    myChoreographerTimer.step();
    assertThat(table.getRowCount(), is(2));

    dataRange.set(0, 0);
    myChoreographerTimer.step();
    assertThat(table.getRowCount(), is(0));
  }

  @Test
  public void activeConnectionIsAutoFocusedByTable() throws Exception {
    Range dataRange = new Range();
    ConnectionsView view = new ConnectionsView(myStage, myChoreographer, dataRange, data -> {});

    JTable table = getConnectionsTable(view);
    final int[] selectedRow = {-1};

    // We arbitrarily select one of the fake data instances and sanity check that the table
    // auto-selects it, which is checked for below.
    int arbitraryIndex = 1;
    HttpData activeData = convert(FakeNetworkService.FAKE_DATA.get(arbitraryIndex));
    myStage.setConnection(activeData);

    CountDownLatch latchSelected = new CountDownLatch(1);
    table.getSelectionModel().addListSelectionListener(e -> {
      selectedRow[0] = e.getFirstIndex();
      latchSelected.countDown();
    });

    dataRange.set(0, TimeUnit.SECONDS.toMicros(100));
    myChoreographerTimer.step();
    latchSelected.await();
    assertThat(selectedRow[0], is(arbitraryIndex));
  }

  @Test
  public void tableCanBeSorted() throws Exception {
    Range dataRange = new Range(0, TimeUnit.SECONDS.toMicros(100));
    ConnectionsView view = new ConnectionsView(myStage, myChoreographer, dataRange, data -> {});

    JTable table = getConnectionsTable(view);

    // Times: 1, 2, 5, 13. Should sort numerically, not alphabetically (e.g. not 1, 13, 2, 5)
    // Toggle once for ascending, twice for descending
    table.getRowSorter().toggleSortOrder(ConnectionsView.Column.TIME.ordinal());
    table.getRowSorter().toggleSortOrder(ConnectionsView.Column.TIME.ordinal());

    myChoreographerTimer.step();
    // After reverse sorting, data should be backwards
    assertThat(table.getRowCount(), is(4));
    assertThat(table.convertRowIndexToView(0), is(3));
    assertThat(table.convertRowIndexToView(1), is(2));
    assertThat(table.convertRowIndexToView(2), is(1));
    assertThat(table.convertRowIndexToView(3), is(0));

    dataRange.set(0, 0);
    myChoreographerTimer.step();
    assertThat(table.getRowCount(), is(0));

    // Include middle two requests: 3->5 (time = 2), and 8->13 (time=5)
    // This should still be shown in reverse sorted over
    dataRange.set(TimeUnit.SECONDS.toMicros(3), TimeUnit.SECONDS.toMicros(10));
    myChoreographerTimer.step();
    assertThat(table.getRowCount(), is(2));
    assertThat(table.convertRowIndexToView(0), is(1));
    assertThat(table.convertRowIndexToView(1), is(0));
  }

  @Test
  public void testTableRowHighlight() {
    Range dataRange = new Range(0, TimeUnit.SECONDS.toMicros(100));
    ConnectionsView view = new ConnectionsView(myStage, myChoreographer, dataRange, data -> {});
    int timelineColumn = ConnectionsView.Column.TIMELINE.ordinal();
    JTable table = getConnectionsTable(view);

    myChoreographerTimer.step();

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
  public void ensureAxisInFirstRow() throws Exception {
    Range dataRange = new Range(0, TimeUnit.SECONDS.toMicros(100));
    ConnectionsView view = new ConnectionsView(myStage, myChoreographer, dataRange, data -> {});

    JTable table = getConnectionsTable(view);

    myChoreographerTimer.step();

    int timelineColumn = ConnectionsView.Column.TIMELINE.ordinal();
    TableCellRenderer renderer = table.getCellRenderer(1, timelineColumn);

    Component comp = table.prepareRenderer(renderer, 0, timelineColumn);
    assertThat(comp instanceof JPanel, is(true));
    assertThat(((JPanel)comp).getComponent(0), instanceOf(AxisComponent.class));
    assertThat(((JPanel)comp).getComponent(1), instanceOf(StateChart.class));
  }

  private static HttpData convert(NetworkProfiler.HttpConnectionData sourceData) {
    long id = sourceData.getConnId();
    long start = TimeUnit.NANOSECONDS.toMicros(sourceData.getStartTimestamp());
    long end = TimeUnit.NANOSECONDS.toMicros(sourceData.getEndTimestamp());
    return new HttpData.Builder(id, start, end, 0)
      .setUrl("http://example.com/" + id)
      .setResponseFields("null = HTTP/1.1 200 OK;\nContent-Length = " + id + "00;\nContent-Type = image/jpeg;")
      .build();
  }

  /**
   * The underlying table in ConnectionsView is intentionally not exposed to regular users of the
   * class. However, for tests, it is useful to inspect the contents of the table to verify it was
   * updated.
   */
  private static JTable getConnectionsTable(ConnectionsView view) {
    return (JTable)view.getComponent();
  }

  // TODO: use TestNetworkService instead
  private static final class FakeNetworkService extends NetworkServiceGrpc.NetworkServiceImplBase {

    public static final ImmutableList<NetworkProfiler.HttpConnectionData> FAKE_DATA =
      new ImmutableList.Builder<NetworkProfiler.HttpConnectionData>()
        .add(newData(1, TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(2)))
        .add(newData(2, TimeUnit.SECONDS.toMicros(3), TimeUnit.SECONDS.toMicros(5)))
        .add(newData(3, TimeUnit.SECONDS.toMicros(8), TimeUnit.SECONDS.toMicros(13)))
        .add(newData(4, TimeUnit.SECONDS.toMicros(21), TimeUnit.SECONDS.toMicros(34)))
        .build();

    @Override
    public void getHttpRange(NetworkProfiler.HttpRangeRequest request, StreamObserver<NetworkProfiler.HttpRangeResponse> responseObserver) {
      NetworkProfiler.HttpRangeResponse.Builder builder = NetworkProfiler.HttpRangeResponse.newBuilder();
      for (NetworkProfiler.HttpConnectionData d : FAKE_DATA) {
        // Data should be included as long as one end or the other (or both) overlaps with the request range
        if ((request.getStartTimestamp() <= d.getEndTimestamp() && d.getEndTimestamp() <= request.getEndTimestamp()) ||
          request.getStartTimestamp() <= d.getStartTimestamp() && d.getStartTimestamp() <= request.getEndTimestamp()) {
          builder.addData(d);
        }
      }
      NetworkProfiler.HttpRangeResponse response = builder.build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    private static NetworkProfiler.HttpConnectionData newData(long id, long startUs, long endUs) {
      NetworkProfiler.HttpConnectionData.Builder builder = NetworkProfiler.HttpConnectionData.newBuilder();
      builder.setConnId(id);
      builder.setStartTimestamp(TimeUnit.MICROSECONDS.toNanos(startUs));
      builder.setEndTimestamp(TimeUnit.MICROSECONDS.toNanos(endUs));
      return builder.build();
    }
  }
}