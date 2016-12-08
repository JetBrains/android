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
package com.android.tools.adtui.common;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;

import static com.google.common.truth.Truth.assertThat;

public class EnumColorsTest {
  private Color COLOR_A_0 = Color.RED;
  private Color COLOR_B_0 = Color.GREEN;
  private Color COLOR_C_0 = Color.BLUE;
  private Color COLOR_A_1 = Color.BLACK;
  private Color COLOR_B_1 = Color.GRAY;
  private Color COLOR_C_1 = Color.WHITE;

  @Test
  public void createSimpleColorGroupUsingMap() throws Exception {
    ImmutableMap<TestStates, Color> colorMap = ImmutableMap.<TestStates, Color>builder()
      .put(TestStates.A, COLOR_A_0)
      .put(TestStates.B, COLOR_B_0)
      .put(TestStates.C, COLOR_C_0)
      .build();
    EnumColors<TestStates> g = new EnumColors<>(colorMap);
    assertThat(g.getColor(TestStates.A)).isEqualTo(COLOR_A_0);
    assertThat(g.getColor(TestStates.B)).isEqualTo(COLOR_B_0);
    assertThat(g.getColor(TestStates.C)).isEqualTo(COLOR_C_0);
  }

  @Test
  public void createSimpleColorGroupUsingBuilder() throws Exception {
    EnumColors<TestStates> g = new EnumColors.Builder<TestStates>(1)
      .add(TestStates.A, COLOR_A_0)
      .add(TestStates.B, COLOR_B_0)
      .add(TestStates.C, COLOR_C_0)
      .build();
    assertThat(g.getColor(TestStates.A)).isEqualTo(COLOR_A_0);
    assertThat(g.getColor(TestStates.B)).isEqualTo(COLOR_B_0);
    assertThat(g.getColor(TestStates.C)).isEqualTo(COLOR_C_0);
  }

  @Test
  public void createTwoDimensionalColorGroup() throws Exception {
    EnumColors<TestStates> g = new EnumColors.Builder<TestStates>(2)
      .add(TestStates.A, COLOR_A_0, COLOR_A_1)
      .add(TestStates.B, COLOR_B_0, COLOR_B_1)
      .add(TestStates.C, COLOR_C_0, COLOR_C_1)
      .build();
    assertThat(g.getColor(TestStates.A)).isEqualTo(COLOR_A_0);
    assertThat(g.getColor(TestStates.B)).isEqualTo(COLOR_B_0);
    assertThat(g.getColor(TestStates.C)).isEqualTo(COLOR_C_0);

    g.setColorIndex(1);
    assertThat(g.getColor(TestStates.A)).isEqualTo(COLOR_A_1);
    assertThat(g.getColor(TestStates.B)).isEqualTo(COLOR_B_1);
    assertThat(g.getColor(TestStates.C)).isEqualTo(COLOR_C_1);

    g.setColorIndex(2);
    assertThat(g.getColor(TestStates.A)).isEqualTo(COLOR_A_0);
    assertThat(g.getColor(TestStates.B)).isEqualTo(COLOR_B_0);
    assertThat(g.getColor(TestStates.C)).isEqualTo(COLOR_C_0);
  }

  @Test
  public void builderRequiresCorrectNumberOfArguments() throws Exception {
    try {
      new EnumColors.Builder<TestStates>(2)
        .add(TestStates.A, COLOR_A_0);
      Assert.fail();
    }
    catch (IllegalArgumentException ignored) {}

    try {
      new EnumColors.Builder<TestStates>(2)
        .add(TestStates.A, COLOR_A_0, COLOR_B_0, COLOR_C_0);
      Assert.fail();
    }
    catch (IllegalArgumentException ignored) {}


  }

  private enum TestStates {
    A, B, C
  }
}