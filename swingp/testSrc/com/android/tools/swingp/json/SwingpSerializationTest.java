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

import com.android.tools.swingp.*;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.reference.SoftReference;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SwingpSerializationTest {
  @Test
  public void affineTransformCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();
    AffineTransform transform = new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);

    assertThat(gson.toJson(transform)).isEqualTo("[1.0,2.0,3.0,4.0,5.0,6.0]");
  }

  @Test
  public void pointCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();
    Point point = new Point(1, 2);

    assertThat(gson.toJson(point)).isEqualTo("[1,2]");
  }

  @Test
  public void softReferenceCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    String unusedValue = "DUMMY";
    SoftReference<String> setReference = new SoftReference<>(unusedValue);
    SoftReference<String> unsetReference = new SoftReference<>(null);

    assertThat(gson.toJsonTree(setReference).getAsString()).isEqualTo("String");
    assertThat(gson.toJsonTree(unsetReference).getAsString()).isEqualTo("<gc>");
  }

  @Test
  public void bufferStrategyPaintMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Object owner = new Object();
    BufferStrategyPaintMethodStat stat = new BufferStrategyPaintMethodStat(owner, true);

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();
    assertThat(statObj.keySet()).containsAllIn(Lists.newArrayList(
      "callee",
      "endTime",
      "isBufferStrategy",
      "owner",
      "startTime"
      ));
  }

  @Test
  public void paintChildrenMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    JLabel dummyLabel = new JLabel("DUMMY");
    AffineTransform transform = new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);

    PaintChildrenMethodStat stat = new PaintChildrenMethodStat(dummyLabel, transform);
    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();
    assertThat(statObj.keySet()).containsAllIn(Lists.newArrayList(
      "callee",
      "endTime",
      "owner",
      "startTime",
      "xform"));
  }

  @Test
  public void paintComponentMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    JLabel dummyLabel = new JLabel("DUMMY");
    AffineTransform transform = new AffineTransform(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);
    Graphics g = mock(Graphics2D.class);

    PaintComponentMethodStat stat = new PaintComponentMethodStat(dummyLabel, g, transform, -1, -2, 3, 4);

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();
    assertThat(statObj.keySet()).containsAllIn(Lists.newArrayList(
      "callee",
      "clip",
      "endTime",
      "isImage",
      "owner",
      "startTime",
      "xform"));
  }

  @Test
  public void paintImmediatelyMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Object owner = new Object();
    JLabel label = new JLabel("DUMMY");
    Graphics g = mock(Graphics2D.class);
    when(((Graphics2D)g).getTransform()).thenReturn(new AffineTransform());

    PaintImmediatelyMethodStat stat = new PaintImmediatelyMethodStat(owner, label, g, -1, -2, 3, 4);

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();
    assertThat(statObj.keySet()).containsAllIn(Lists.newArrayList(
      "bounds",
      "bufferBounds",
      "bufferId",
      "bufferType",
      "callee",
      "classType",
      "constrain",
      "endTime",
      "owner",
      "startTime",
      "xform"));
  }

  @Test
  public void windowPaintMethodStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Window window = mock(Window.class);

    WindowPaintMethodStat stat = new WindowPaintMethodStat(window);

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();
    assertThat(statObj.keySet()).containsAllIn(Lists.newArrayList(
      "callee",
      "endTime",
      "location",
      "owner",
      "startTime",
      "xform",
      "windowId"));
  }

  @Test
  public void threadStatCanBeSerialized() {
    Gson gson = RenderStatsManager.createSwingpGson();

    Thread thread = mock(Thread.class);
    when(thread.getId()).thenReturn((long)123);
    thread.setName("DUMMY");

    ThreadStat stat = new ThreadStat(thread).setIsRecording(true);
    stat.pushMethod(new MethodStat(new Object()) {
    });

    JsonObject statObj = gson.toJsonTree(stat).getAsJsonObject();
    assertThat(statObj.keySet()).containsAllIn(Lists.newArrayList(
      "classType",
      "events",
      "threadId",
      "threadName"
    ));
  }
}
