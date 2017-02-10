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
package com.android.tools.adtui;

import com.android.tools.adtui.model.legend.FixedLegend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class LegendComponentTest {

  /**
   * Verifies that adding/removing legends in the model between updates would keep the internal states of LegendComponent in sync.
   */
  @Test
  public void testAddRemoveLegendBetweenUpdates() throws Exception {
    LegendComponentModel model = new LegendComponentModel(0);
    FixedLegend fixedLegend1 = new FixedLegend("Test1");
    FixedLegend fixedLegend2 = new FixedLegend("Test2");
    LegendComponent legendComponent = new LegendComponent(model);
    assertEquals(0, legendComponent.getLabelsToDraw().size());

    model.add(fixedLegend1);
    model.update(TimeUnit.SECONDS.toNanos(1));
    ImmutableList<JLabel> labels = legendComponent.getLabelsToDraw();
    assertEquals(1, labels.size());
    assertEquals("Test1", labels.get(0).getText());

    model.add(fixedLegend2);
    labels = legendComponent.getLabelsToDraw();
    assertEquals(2, labels.size());
    assertEquals("Test1", labels.get(0).getText());
    assertEquals("Test2", labels.get(1).getText());

    model.remove(fixedLegend1);
    model.update(TimeUnit.SECONDS.toNanos(1));
    labels = legendComponent.getLabelsToDraw();
    assertEquals(1, labels.size());
    assertEquals("Test2", labels.get(0).getText());

    model.remove(fixedLegend2);
    labels = legendComponent.getLabelsToDraw();
    assertEquals(0, labels.size());
  }
}