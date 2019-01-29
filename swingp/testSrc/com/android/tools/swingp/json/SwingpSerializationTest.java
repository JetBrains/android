/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.swingp.json;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.swingp.BufferStrategyPaintMethodStat;
import com.android.tools.swingp.MethodStat;
import com.android.tools.swingp.PaintChildrenMethodStat;
import com.android.tools.swingp.PaintComponentMethodStat;
import com.android.tools.swingp.PaintImmediatelyMethodStat;
import com.android.tools.swingp.RenderStatsManager;
import com.android.tools.swingp.ThreadStat;
import com.android.tools.swingp.WindowPaintMethodStat;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.lang.ref.SoftReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.Test;

public class SwingpSerializationTest {
  @Test
  public void affineTransformCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();
    AffineTransform transform = new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);

    assertThat(gson.toJsonTree(transform).getAsJsonArray()).containsExactly(
      new JsonPrimitive(1.0f),
      new JsonPrimitive(2.0f),
      new JsonPrimitive(3.0f),
      new JsonPrimitive(4.0f),
      new JsonPrimitive(5.0f),
      new JsonPrimitive(6.0f)).inOrder();
  }

  @Test
  public void pointCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();
    Point point = new Point(1, 2);

    assertThat(gson.toJsonTree(point).getAsJsonArray()).containsExactly(
      new JsonPrimitive(1),
      new JsonPrimitive(2)).inOrder();
  }

  @Test
  public void softReferenceCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    String unusedValue = "DUMMY";
    SoftReference<String> setReference = new SoftReference<>(unusedValue);
    SoftReference<String> unsetReference = new SoftReference<>(null);

    assertThat(gson.toJsonTree(setReference).getAsJsonPrimitive().getAsString()).isEqualTo("String");
    assertThat(gson.toJsonTree(unsetReference).getAsJsonPrimitive().getAsString()).isEqualTo("<gc>");
  }

  @Test
  public void bufferStrategyPaintMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Object owner = new Object();
    BufferStrategyPaintMethodStat stat = new BufferStrategyPaintMethodStat(owner, true);
    stat.endMethod();

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();

    assertThat(statObj.getAsJsonPrimitive("classType").getAsString()).isEqualTo("BufferStrategyPaintMethodStat");
    assertThat(statObj.getAsJsonPrimitive("owner").getAsString()).isEqualTo("Object");
    assertThat(statObj.getAsJsonPrimitive("isBufferStrategy").getAsBoolean()).isTrue();
    assertThat(statObj.getAsJsonPrimitive("startTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("endTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonArray("callee")).isEmpty();
  }

  @Test
  public void paintChildrenMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    JLabel dummyLabel = new JLabel("DUMMY");
    AffineTransform transform = new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);

    PaintChildrenMethodStat stat = new PaintChildrenMethodStat(dummyLabel, transform);
    stat.endMethod();

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();

    assertThat(statObj.getAsJsonPrimitive("classType").getAsString()).isEqualTo("PaintChildrenMethodStat");
    assertThat(statObj.getAsJsonPrimitive("owner").getAsString()).isEqualTo("JLabel");
    assertThat(statObj.getAsJsonPrimitive("startTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("endTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonArray("callee")).isEmpty();
    assertThat(statObj.getAsJsonArray("xform")).containsExactly(
      new JsonPrimitive(1.0f),
      new JsonPrimitive(2.0f),
      new JsonPrimitive(3.0f),
      new JsonPrimitive(4.0f),
      new JsonPrimitive(5.0f),
      new JsonPrimitive(6.0f)).inOrder();
  }

  @Test
  public void paintComponentMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    JLabel dummyLabel = new JLabel("DUMMY");
    AffineTransform transform = new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);
    Graphics g = mock(Graphics2D.class);

    PaintComponentMethodStat stat = new PaintComponentMethodStat(dummyLabel, g, transform, -1, -2, 3, 4);
    stat.endMethod();

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();

    assertThat(statObj.getAsJsonPrimitive("classType").getAsString()).isEqualTo("PaintComponentMethodStat");
    assertThat(statObj.getAsJsonPrimitive("owner").getAsString()).isEqualTo("JLabel");
    assertThat(statObj.getAsJsonPrimitive("startTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("endTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("isImage").getAsBoolean()).isFalse();

    assertThat(statObj.getAsJsonArray("clip")).containsExactly(
      new JsonPrimitive(-1),
      new JsonPrimitive(-2),
      new JsonPrimitive(3),
      new JsonPrimitive(4)).inOrder();
    assertThat(statObj.getAsJsonArray("xform")).containsExactly(
      new JsonPrimitive(1.0f),
      new JsonPrimitive(2.0f),
      new JsonPrimitive(3.0f),
      new JsonPrimitive(4.0f),
      new JsonPrimitive(5.0f),
      new JsonPrimitive(6.0f)).inOrder();

    assertThat(statObj.getAsJsonArray("callee")).isEmpty();
  }

  @Test
  public void paintImmediatelyMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Object owner = new Object();
    JPanel panel = new JPanel();
    panel.setSize(123, 456);
    Graphics2D g = mock(Graphics2D.class);
    when(g.getTransform()).thenReturn(new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f));

    PaintImmediatelyMethodStat stat = new PaintImmediatelyMethodStat(owner, panel, g, -1, -2, 3, 4);
    stat.endMethod();

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();

    assertThat(statObj.getAsJsonPrimitive("classType").getAsString()).isEqualTo("PaintImmediatelyMethodStat");
    assertThat(statObj.getAsJsonPrimitive("owner").getAsString()).isEqualTo("Object");
    assertThat(statObj.getAsJsonPrimitive("startTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("endTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("bufferType").getAsString()).isEqualTo("JPanel");
    assertThat(statObj.getAsJsonPrimitive("bufferId").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("endTime").getAsLong()).isNotEqualTo(0);

    assertThat(statObj.getAsJsonArray("bounds")).containsExactly(
      new JsonPrimitive(-1),
      new JsonPrimitive(-2),
      new JsonPrimitive(3),
      new JsonPrimitive(4)).inOrder();
    assertThat(statObj.getAsJsonArray("bufferBounds")).containsExactly(
      new JsonPrimitive(0),
      new JsonPrimitive(0),
      new JsonPrimitive(123),
      new JsonPrimitive(456)).inOrder();
    assertThat(statObj.getAsJsonArray("constrain")).containsExactly(
      new JsonPrimitive(0),
      new JsonPrimitive(0)).inOrder();
    assertThat(statObj.getAsJsonArray("xform")).containsExactly(
      new JsonPrimitive(1.0f),
      new JsonPrimitive(2.0f),
      new JsonPrimitive(3.0f),
      new JsonPrimitive(4.0f),
      new JsonPrimitive(5.0f),
      new JsonPrimitive(6.0f)).inOrder();

    assertThat(statObj.getAsJsonArray("callee")).isEmpty();
  }

  @Test
  public void windowPaintMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Container parent = mock(Container.class);
    when(parent.isShowing()).thenReturn(false);

    Graphics2D g = mock(Graphics2D.class);
    when(g.getTransform()).thenReturn(new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f));

    Window window = mock(Window.class);
    when(window.getGraphics()).thenReturn(g);
    when(window.getParent()).thenReturn(parent);
    when(window.getLocationOnScreen()).thenReturn(new Point(123, 456));

    WindowPaintMethodStat stat = new WindowPaintMethodStat(window);
    stat.endMethod();

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();

    assertThat(statObj.getAsJsonPrimitive("classType").getAsString()).isEqualTo("WindowPaintMethodStat");
    // Actual name is Window$MockitoMock$<ID>. Would be "Window" in production.
    assertThat(statObj.getAsJsonPrimitive("owner").getAsString()).startsWith("Window");
    assertThat(statObj.getAsJsonPrimitive("startTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("endTime").getAsLong()).isNotEqualTo(0);
    assertThat(statObj.getAsJsonPrimitive("windowId").getAsLong()).isNotEqualTo(0);

    assertThat(statObj.getAsJsonArray("location")).containsExactly(
      new JsonPrimitive(123),
      new JsonPrimitive(456)).inOrder();

    assertThat(statObj.getAsJsonArray("callee")).isEmpty();
  }

  @Test
  public void threadStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Thread thread = mock(Thread.class);
    when(thread.getId()).thenReturn((long)123);
    thread.setName("FakeThread");

    ThreadStat stat = new ThreadStat(thread).setIsRecording(true);
    stat.pushMethod(new MethodStat(new Object()) {
    });

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();

    assertThat(statObj.getAsJsonPrimitive("classType").getAsString()).isEqualTo("ThreadStat");
    assertThat(statObj.getAsJsonPrimitive("threadName").getAsString()).isEqualTo("FakeThread");
    assertThat(statObj.getAsJsonPrimitive("threadId").getAsLong()).isEqualTo(123);

    assertThat(statObj.getAsJsonArray("events")).isEmpty();
  }
}
