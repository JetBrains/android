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
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ThreadsViewTest {
  public static final ImmutableList<HttpData> FAKE_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(newData(1, 0, 5, 10, 11, "threadA"))
      .add(newData(2, 5, 10, 12, 12, "threadB"))
      .add(newData(3, 13, 14, 15, 11, "threadA"))
      .add(newData(4, 20, 21, 25, 11, "threadA"))
      .add(newData(5, 14, 16, 21, 12, "threadB"))

      .add(newData(11, 100, 101, 110, 13, "threadC"))
      .add(newData(12, 115, 116, 120, 14, "threadC"))
      .build();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("ThreadsViewTest", new FakeProfilerService(false),
                                                                   FakeNetworkService.newBuilder().setHttpDataList(FAKE_DATA).build());
  private NetworkProfilerStageView myStageView;
  private ThreadsView myView;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer());
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    myStageView = new NetworkProfilerStageView(profilersView, new NetworkProfilerStage(profilers));
    myView = new ThreadsView(myStageView);
  }

  @Test
  public void showsCorrectThreadData() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JList<List<HttpData>> list = getList();

    selection.set(0, TimeUnit.SECONDS.toMicros(22));
    assertThat(list.getModel().getSize(), is(2));
    assertThat(list.getModel().getElementAt(0), is(Arrays.asList(FAKE_DATA.get(0), FAKE_DATA.get(2), FAKE_DATA.get(3))));
    assertThat(list.getModel().getElementAt(1), is(Arrays.asList(FAKE_DATA.get(1), FAKE_DATA.get(4))));
  }

  @Test
  public void shouldHandleEmptySelection() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JList<List<HttpData>> list = getList();

    selection.set(0, TimeUnit.SECONDS.toMicros(22));
    assertThat(list.getModel().getSize(), is(2));

    selection.clear();
    assertThat(list.getModel().getSize(), is(0));
  }

  @Test
  public void shouldHandleThreadsWithTheSameNameButDifferentID() {
    Range selection = myStageView.getTimeline().getSelectionRange();
    JList<List<HttpData>> list = getList();

    selection.set(TimeUnit.SECONDS.toMicros(99), TimeUnit.SECONDS.toMicros(120));
    assertThat(list.getModel().getSize(), is(2));
    assertThat(list.getModel().getElementAt(0), is(Collections.singletonList(FAKE_DATA.get(5))));
    assertThat(list.getModel().getElementAt(1), is(Collections.singletonList(FAKE_DATA.get(6))));
  }

  @Test
  public void ensureAxisInList() {
    JList<List<HttpData>> list = this.getList();
    Range selection = myStageView.getTimeline().getSelectionRange();
    selection.set(0, TimeUnit.SECONDS.toMicros(22));

    Component rendered = list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false);
    assertTrue(new TreeWalker(rendered).descendantStream().anyMatch(c -> c instanceof AxisComponent));
  }

  @NotNull
  private JList<List<HttpData>> getList() {
    return (JList<List<HttpData>>)myView.getComponent();
  }

  @NotNull
  private static HttpData newData(long id, long start, long download, long end, long threadId, String threadName) {
    return FakeNetworkService.newHttpDataBuilder(id, start, download, end)
      .setJavaThread(new HttpData.JavaThread(threadId, threadName))
      .build();
  }
}