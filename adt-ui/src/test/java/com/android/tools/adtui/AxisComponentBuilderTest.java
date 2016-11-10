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
package com.android.tools.adtui;

import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.Range;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class AxisComponentBuilderTest {

  @Test
  public void testBuilder() throws Exception {
    // A simple test to validate that the AxisComponent generated from the Builder has consistent values as the input.
    AnimatedRange testRange1 = new AnimatedRange(0, 100);
    TimeAxisFormatter testFormatter1 = new TimeAxisFormatter(5, 5, 10);
    AxisComponent.Builder builder = new AxisComponent.Builder(testRange1, testFormatter1, AxisComponent.AxisOrientation.BOTTOM);

    AxisComponent axis1 = builder.build();
    assertThat(axis1.getRange()).isEqualTo(testRange1);
    assertThat(axis1.getLabel()).isNull();
    assertThat(axis1.getAxisFormatter()).isEqualTo(testFormatter1);
    assertThat(axis1.getOrientation()).isEqualTo(AxisComponent.AxisOrientation.BOTTOM);
    assertThat(axis1.getGlobalRange()).isNull();
    assertThat(axis1.getParentAxis()).isNull();
    assertThat(axis1.getClampToMajorTicks()).isFalse();
    assertThat(axis1.getShowMinMax()).isFalse();
    assertThat(axis1.getShowAxisLine()).isTrue();

    Range testRange2 = new Range(0, 200);
    builder = new AxisComponent.Builder(testRange1, testFormatter1, AxisComponent.AxisOrientation.TOP)
      .setParentAxis(axis1)
      .setGlobalRange(testRange2)
      .setLabel("Axis")
      .showMinMax(true)
      .showAxisLine(false)
      .clampToMajorTicks(true);

    AxisComponent axis2 = builder.build();
    assertThat(axis2.getRange()).isEqualTo(testRange1);
    assertThat(axis2.getLabel()).isEqualTo("Axis");
    assertThat(axis2.getAxisFormatter()).isEqualTo(testFormatter1);
    assertThat(axis2.getOrientation()).isEqualTo(AxisComponent.AxisOrientation.TOP);
    assertThat(axis2.getGlobalRange()).isEqualTo(testRange2);
    assertThat(axis2.getParentAxis()).isEqualTo(axis1);
    assertThat(axis2.getClampToMajorTicks()).isTrue();
    assertThat(axis2.getShowMinMax()).isTrue();
    assertThat(axis2.getShowAxisLine()).isFalse();

  }
}