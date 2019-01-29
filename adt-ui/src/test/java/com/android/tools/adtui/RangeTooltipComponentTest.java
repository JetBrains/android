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
package com.android.tools.adtui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.Range;
import java.awt.Rectangle;
import javax.swing.JPanel;
import org.junit.Test;

public class RangeTooltipComponentTest {
  @Test
  public void ensureRangeAndXTransformsAreIdentity() {
    Range highlightRange = new Range(50, 50);
    Range viewRange = new Range(23, 79); // Use prime numbers to ensure relative primes.
    Range dataRange = new Range(0, 100);
    JPanel backingComponent = new JPanel();

    Rectangle componentSize = new Rectangle(0, 0, 100, 100);
    backingComponent.setBounds(componentSize);
    RangeTooltipComponent rangeTooltipComponent = new RangeTooltipComponent(highlightRange, viewRange, dataRange, backingComponent);
    rangeTooltipComponent.setBounds(componentSize);

    assertThat(rangeTooltipComponent.rangeToX(rangeTooltipComponent.xToRange(41))).isWithin(1e-6f).of(41.0f);
  }
}
