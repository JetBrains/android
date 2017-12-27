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

import com.intellij.util.ui.JBUI;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public final class TabularLayoutTest {
  @Test
  public void columnCountMatchesLayoutDefinition() {
    TabularLayout layout = new TabularLayout("Fit,*,123px");
    assertThat(layout.getNumColumns()).isEqualTo(3);
  }

  @Test
  public void minimumWidthCalculationUsesFitValues() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("Fit,Fit"));

    Component col0 = Box.createHorizontalStrut(80);
    Component col1 = Box.createHorizontalStrut(20);

    panel.add(col0, new TabularLayout.Constraint(0, 0));
    panel.add(col1, new TabularLayout.Constraint(0, 1));

    mockPackPanel(panel);

    assertThat(panel.getWidth()).isEqualTo(100);
  }

  @Test
  public void minimumWidthCalculationFixedValuesOverrideComponentSizes() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("100px,50px"));

    Component col0 = Box.createHorizontalStrut(90);
    Component col1 = Box.createHorizontalStrut(90);

    panel.add(col0, new TabularLayout.Constraint(0, 0));
    panel.add(col1, new TabularLayout.Constraint(0, 1));

    mockPackPanel(panel);

    assertThat(panel.getWidth()).isEqualTo(150);
  }

  @Test
  public void fitWidthChoosesLargestValueAcrossMultipleRows() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("Fit,Fit"));

    final Component row0col0 = Box.createHorizontalStrut(100);
    final Component row1col0 = Box.createHorizontalStrut(300);
    final Component row3col0 = Box.createHorizontalStrut(200);

    final Component row0col1 = Box.createHorizontalStrut(500);
    final Component row2col1 = Box.createHorizontalStrut(400);
    final Component row4col1 = Box.createHorizontalStrut(100);

    panel.add(row0col0, new TabularLayout.Constraint(0, 0));
    panel.add(row1col0, new TabularLayout.Constraint(1, 0));
    panel.add(row3col0, new TabularLayout.Constraint(3, 0));

    panel.add(row0col1, new TabularLayout.Constraint(0, 1));
    panel.add(row2col1, new TabularLayout.Constraint(2, 1));
    panel.add(row4col1, new TabularLayout.Constraint(4, 1));

    mockPackPanel(panel);

    assertThat(panel.getWidth()).isEqualTo(800);

    assertThat(row0col0.getWidth()).isEqualTo(300);
    assertThat(row1col0.getWidth()).isEqualTo(300);
    assertThat(row3col0.getWidth()).isEqualTo(300);

    assertThat(row0col1.getWidth()).isEqualTo(500);
    assertThat(row2col1.getWidth()).isEqualTo(500);
    assertThat(row4col1.getWidth()).isEqualTo(500);
  }

  @Test
  public void fitWidthUsesComponentMinimumSizeNotPreferredSize() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("Fit"));

    JPanel cell = new JPanel();
    cell.setMinimumSize(new Dimension(5, 10));
    cell.setPreferredSize(new Dimension(15, 25));

    panel.add(cell, new TabularLayout.Constraint(0, 0));
    mockPackPanel(panel);

    assertThat(panel.getWidth()).isEqualTo(5);
    assertThat(panel.getHeight()).isEqualTo(10);
  }

  @Test
  public void proportionalColumnsTakeRemainingSpace() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("100px,Fit,3*,*,50px"));
    panel.setPreferredSize(new Dimension(300, 20));

    // Col 1 = 100, Col 5 = 50
    // Col 2 = Fit to size 50
    // Total width is 300
    // Leftover space is 100 pixels

    final Component col0 = new JPanel();
    final Component col1 = Box.createHorizontalStrut(50);
    final Component col2 = new JPanel();
    final Component col3 = new JPanel();
    final Component col4 = new JPanel();

    panel.add(col0, new TabularLayout.Constraint(0, 0));
    panel.add(col1, new TabularLayout.Constraint(0, 1));
    panel.add(col2, new TabularLayout.Constraint(0, 2));
    panel.add(col3, new TabularLayout.Constraint(0, 3));
    panel.add(col4, new TabularLayout.Constraint(0, 4));

    mockPackPanel(panel);

    assertThat(panel.getWidth()).isEqualTo(300);

    assertThat(col0.getWidth()).isEqualTo(100);
    assertThat(col1.getWidth()).isEqualTo(50);
    assertThat(col2.getWidth()).isEqualTo(75);
    assertThat(col3.getWidth()).isEqualTo(25);
    assertThat(col4.getWidth()).isEqualTo(50);
  }

  @Test
  public void preferredSizeCalculationMakesRoomForProportionalColumns() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("*,2*,3*,4*"));

    // Col 0 - 10%
    // Col 1 - 20%
    // Col 2 - 30%
    // Col 3 - 40%

    final Component col0 = Box.createHorizontalStrut(40); // Needs overall width to be 400
    final Component col1 = Box.createHorizontalStrut(50); // Needs overall width to be 250
    final Component col2 = Box.createHorizontalStrut(30); // Needs overall width to be 100
    final Component col3 = Box.createHorizontalStrut(80); // Needs overall width to be 200

    panel.add(col0, new TabularLayout.Constraint(0, 0));
    panel.add(col1, new TabularLayout.Constraint(0, 1));
    panel.add(col2, new TabularLayout.Constraint(0, 2));
    panel.add(col3, new TabularLayout.Constraint(0, 3));

    mockPackPanel(panel);

    assertThat(panel.getWidth()).isEqualTo(400);
    assertThat(col0.getWidth()).isEqualTo(40);
    assertThat(col1.getWidth()).isEqualTo(80);
    assertThat(col2.getWidth()).isEqualTo(120);
    assertThat(col3.getWidth()).isEqualTo(160);
  }

  @Test
  public void minimumSizeCalculationCollapsesProportionalColumns() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("10px,990*,*,20px,3*"));

    mockPackPanel(panel);

    assertThat(panel.getMinimumSize().getWidth()).isWithin(0.1).of(30.0);
  }

  @Test
  public void heightCalculationSkipsEmptyRows() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("100px"));

    final Component row0 = Box.createVerticalStrut(20);
    final Component row2 = Box.createVerticalStrut(50);

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row2, new TabularLayout.Constraint(2, 0));

    mockPackPanel(panel);

    assertThat(panel.getHeight()).isEqualTo(70);

    assertThat(row0.getHeight()).isEqualTo(20);
    assertThat(row2.getY()).isEqualTo(20);
    assertThat(row2.getHeight()).isEqualTo(50);
  }

  @Test
  public void heightCalculationIncludesVgapAndSkipsEmptyRows() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("100px").setVGap(20));

    final Component row2 = Box.createVerticalStrut(20);
    final Component row4 = Box.createVerticalStrut(40);
    final Component row6 = Box.createVerticalStrut(60);

    panel.add(row2, new TabularLayout.Constraint(2, 0));
    panel.add(row4, new TabularLayout.Constraint(4, 0));
    panel.add(row6, new TabularLayout.Constraint(6, 0));

    mockPackPanel(panel);

    assertThat(panel.getHeight()).isEqualTo(120 + 40); // 120 from struts, 40 from inner gaps

    assertThat(row2.getY()).isEqualTo(0);
    assertThat(row2.getHeight()).isEqualTo(20);
    assertThat(row4.getY()).isEqualTo(40);
    assertThat(row4.getHeight()).isEqualTo(40);
    assertThat(row6.getY()).isEqualTo(100);
    assertThat(row6.getHeight()).isEqualTo(60);
  }

  @Test
  public void heightCalculationSkipsInvisibleRows() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("100px"));

    final Component row0 = Box.createVerticalStrut(20);
    final Component row1 = Box.createVerticalStrut(50);
    final Component row2 = Box.createVerticalStrut(20);

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));
    panel.add(row2, new TabularLayout.Constraint(2, 0));

    row1.setVisible(false);
    mockPackPanel(panel);
    assertThat(panel.getHeight()).isEqualTo(40);

    row1.setVisible(true);
    mockPackPanel(panel);
    assertThat(panel.getHeight()).isEqualTo(90);
  }

  @Test
  public void proportionalLayoutCollapsesIfAllContentsAreInvisible() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("Fit"));

    final Component row0 = Box.createVerticalStrut(20);
    final Component row1 = Box.createVerticalStrut(50);
    final Component row2 = Box.createVerticalStrut(20);

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));
    panel.add(row2, new TabularLayout.Constraint(2, 0));

    row0.setVisible(false);
    row1.setVisible(false);
    row2.setVisible(false);
    mockPackPanel(panel);
    assertThat(panel.getHeight()).isEqualTo(0);
    assertThat(panel.getWidth()).isEqualTo(0);
  }

  @Test
  public void layoutTakesInsetsIntoAccount() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("*"));
    panel.setPreferredSize(new Dimension(300, 30));

    final Component cell = new JPanel();
    panel.add(cell, new TabularLayout.Constraint(0, 0));

    final int top = 1;
    final int left = 2;
    final int bottom = 3;
    final int right = 4;
    panel.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));

    mockPackPanel(panel);

    assertThat(panel.getWidth()).isEqualTo(300);
    assertThat(panel.getHeight()).isEqualTo(30);
    assertThat(cell.getWidth()).isEqualTo(300 - left - right);
  }

  @Test
  public void cellsCanSpanAcrossMultipleColumns() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("Fit,Fit"));

    final Component row0col0 = Box.createHorizontalStrut(20);
    final Component row0col1 = Box.createHorizontalStrut(50);
    final JPanel row1 = new JPanel();
    final Component row2col0 = Box.createHorizontalStrut(10);
    final Component row2col1 = Box.createHorizontalStrut(100);

    panel.add(row0col0, new TabularLayout.Constraint(0, 0));
    panel.add(row0col1, new TabularLayout.Constraint(0, 1));
    panel.add(row1, new TabularLayout.Constraint(1, 0, 2));
    panel.add(row2col0, new TabularLayout.Constraint(2, 0));
    panel.add(row2col1, new TabularLayout.Constraint(2, 1));

    mockPackPanel(panel);
    assertThat(row1.getWidth()).isEqualTo(120);
  }

  @Test
  public void columnSpanMustBeWithinBounds() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("Fit,Fit"));
    final JPanel row = new JPanel();

    try {
      panel.add(row, new TabularLayout.Constraint(0, 0, 3));
      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void columnSpanMustBeGreaterThanZero() throws Exception {
    final JPanel panel = new JPanel(new TabularLayout("Fit,Fit"));
    final JPanel row = new JPanel();

    try {
      panel.add(row, new TabularLayout.Constraint(0, 0, 0));
      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void rowsCanBeConfiguredWithFixedHeights() throws Exception {
    TabularLayout layout = new TabularLayout("50px", "100px,200px,300px");

    final JPanel panel = new JPanel(layout);
    final Component row0 = new JPanel();
    final Component row1 = new JPanel();

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));

    mockPackPanel(panel);

    assertThat(panel.getHeight()).isEqualTo(600);
    assertThat(row0.getHeight()).isEqualTo(100);
    assertThat(row0.getWidth()).isEqualTo(50);
    assertThat(row1.getHeight()).isEqualTo(200);
    assertThat(row1.getWidth()).isEqualTo(50);
  }

  @Test
  public void rowsCanBeConfiguredDynamically() throws Exception {
    TabularLayout layout = new TabularLayout("50px");
    layout.setRowSizing(0, "100px");
    layout.setRowSizing(1, "200px");
    layout.setRowSizing(2, "300px");

    final JPanel panel = new JPanel(layout);
    final Component row0 = new JPanel();
    final Component row1 = new JPanel();

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));

    mockPackPanel(panel);

    assertThat(panel.getHeight()).isEqualTo(600);
    assertThat(row0.getHeight()).isEqualTo(100);
    assertThat(row0.getWidth()).isEqualTo(50);
    assertThat(row1.getHeight()).isEqualTo(200);
    assertThat(row1.getWidth()).isEqualTo(50);
  }
  @Test
  public void rowsCanBeConfiguredWithProportionalHeights() throws Exception {
    TabularLayout layout = new TabularLayout("*", "*,2*");

    final JPanel panel = new JPanel(layout);
    final Component row0 = new JPanel();
    final Component row1 = new JPanel();

    panel.setPreferredSize(new Dimension(50, 300));

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));

    mockPackPanel(panel);

    assertThat(panel.getHeight()).isEqualTo(300);
    assertThat(row0.getHeight()).isEqualTo(100);
    assertThat(row0.getWidth()).isEqualTo(50);
    assertThat(row1.getHeight()).isEqualTo(200);
    assertThat(row1.getWidth()).isEqualTo(50);
  }

  @Test
  public void cellsCanSpanAcrossMultipleRowsAndColumns() throws Exception {
    TabularLayout layout = new TabularLayout("10px,20px,30px", "100px,200px,300px");
    final JPanel panel = new JPanel(layout);

    final JPanel cellNW = new JPanel();
    final JPanel cellNE = new JPanel();
    final JPanel cellSW = new JPanel();
    final JPanel cellSE = new JPanel();

    panel.add(cellNW, new TabularLayout.Constraint(0, 0, 2, 2));
    panel.add(cellNE, new TabularLayout.Constraint(0, 1, 2, 2));
    panel.add(cellSW, new TabularLayout.Constraint(1, 0, 2, 2));
    panel.add(cellSE, new TabularLayout.Constraint(1, 1, 2, 2));

    mockPackPanel(panel);
    assertThat(cellNW.getWidth()).isEqualTo(30);
    assertThat(cellNW.getHeight()).isEqualTo(300);
    assertThat(cellNE.getWidth()).isEqualTo(50);
    assertThat(cellNE.getHeight()).isEqualTo(300);
    assertThat(cellSW.getWidth()).isEqualTo(30);
    assertThat(cellSW.getHeight()).isEqualTo(500);
    assertThat(cellSE.getWidth()).isEqualTo(50);
    assertThat(cellSE.getHeight()).isEqualTo(500);
  }

  @Test
  public void fixedSizeConsidersScaleFactor() {
    float originalFactor = JBUI.scale(1.0f);
    float scaleFactor = 2f;
    JBUI.setUserScaleFactor(scaleFactor);

    TabularLayout layout = new TabularLayout("50px");
    layout.setRowSizing(0, "100px");
    layout.setRowSizing(1, "200px");

    final JPanel panel = new JPanel(layout);
    final Component row0 = new JPanel();
    final Component row1 = new JPanel();

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));

    mockPackPanel(panel);

    assertThat(panel.getHeight()).isEqualTo((int)scaleFactor * 300);
    assertThat(row0.getHeight()).isEqualTo((int)scaleFactor * 100);
    assertThat(row1.getHeight()).isEqualTo((int)scaleFactor * 200);
    assertThat(row0.getWidth()).isEqualTo((int)scaleFactor * 50);
    assertThat(row1.getWidth()).isEqualTo((int)scaleFactor * 50);

    JBUI.setUserScaleFactor(originalFactor);
  }

  @Test
  public void proportionalSizingHandlesRoundingErrorWhenSizesRoundDown() throws Exception {
    // 3 rows - each row gets 33.333...% of the space. However, since Swing sizes are integers,
    // for a panel of size 100, this would give us "33" * 3, missing a pixel. Tabular layout should
    // add back that missing space somehow.
    TabularLayout layout = new TabularLayout("*", "*,*,*");

    final JPanel panel = new JPanel(layout);
    final Component row0 = new JPanel();
    final Component row1 = new JPanel();
    final Component row2 = new JPanel();

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));
    panel.add(row2, new TabularLayout.Constraint(2, 0));

    panel.setPreferredSize(new Dimension(50, 100));
    mockPackPanel(panel);
    assertThat(row0.getHeight() + row1.getHeight() + row2.getHeight()).isEqualTo(100);
  }

  @Test
  public void proportionalSizingHandlesRoundingErrorWhenSizesRoundUp() throws Exception {
    // 8 rows - each row gets 12.5% of the space. However, since Swing sizes are integers,
    // for a panel of size 100, this would give us "13" * 8, which is 4 pixels too large. Tabular
    // layout should remove out that extra space somehow.
    TabularLayout layout = new TabularLayout("*", "*,*,*,*,*,*,*,*");

    final JPanel panel = new JPanel(layout);
    final Component row0 = new JPanel();
    final Component row1 = new JPanel();
    final Component row2 = new JPanel();
    final Component row3 = new JPanel();
    final Component row4 = new JPanel();
    final Component row5 = new JPanel();
    final Component row6 = new JPanel();
    final Component row7 = new JPanel();

    panel.add(row0, new TabularLayout.Constraint(0, 0));
    panel.add(row1, new TabularLayout.Constraint(1, 0));
    panel.add(row2, new TabularLayout.Constraint(2, 0));
    panel.add(row3, new TabularLayout.Constraint(3, 0));
    panel.add(row4, new TabularLayout.Constraint(4, 0));
    panel.add(row5, new TabularLayout.Constraint(5, 0));
    panel.add(row6, new TabularLayout.Constraint(6, 0));
    panel.add(row7, new TabularLayout.Constraint(7, 0));

    panel.setPreferredSize(new Dimension(50, 100));
    mockPackPanel(panel);
    assertThat(row0.getHeight() +
               row1.getHeight() +
               row2.getHeight() +
               row3.getHeight() +
               row4.getHeight() +
               row5.getHeight() +
               row6.getHeight() +
               row7.getHeight()).isEqualTo(100);
  }

  /**
   * This fake pack method aims to imitate Frame.pack(), which we can't call in headless mode.
   */
  private static void mockPackPanel(JPanel panel) {
    panel.setSize(panel.getPreferredSize());
    panel.doLayout();
  }
}