/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui;

import org.junit.Before;
import org.junit.Test;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;

public class TooltipLabelTest {

  private JPanel myRootPanel;
  private TooltipLabel myTooltipLabel;

  private JPanel myNode1;
  private JPanel myNode2;
  private JPanel myNode1_1;
  private JPanel myNode1_2;
  private JPanel myNode1_1_1;
  private JPanel myNode1_2_1_notooltip;
  private JPanel myNode1_2_2_notooltip;
  private JPanel myNode1_2_2_1_notooltip;
  private JPanel myNode2_1;
  private JPanel myNode2_1_1_notooltip;

  @Before
  public void setUp() throws Exception {
    myRootPanel = new JPanel();

    myNode1 = new JPanel();
    myNode2 = new JPanel();
    myNode1_1 = new JPanel();
    myNode1_2 = new JPanel();
    myNode1_1_1 = new JPanel();
    myNode1_2_1_notooltip = new JPanel();
    myNode1_2_2_notooltip = new JPanel();
    myNode1_2_2_1_notooltip = new JPanel();
    myNode2_1 = new JPanel();
    myNode2_1_1_notooltip = new JPanel();

    myNode1.setToolTipText("node1");
    myNode2.setToolTipText("node2");
    myNode1_1.setToolTipText("node1_1");
    myNode1_2.setToolTipText("node1_2");
    myNode1_1_1.setToolTipText("node1_1_1");
    myNode2_1.setToolTipText("node2_1");

    myTooltipLabel = new TooltipLabel();

    myRootPanel.add(myTooltipLabel);
    myRootPanel.add(myNode1);
    myRootPanel.add(myNode2);
    myNode1.add(myNode1_1);
    myNode1.add(myNode1_2);
    myNode1_1.add(myNode1_1_1);
    myNode1_2.add(myNode1_2_1_notooltip);
    myNode1_2.add(myNode1_2_2_notooltip);
    myNode1_2_2_notooltip.add(myNode1_2_2_1_notooltip);
    myNode2.add(myNode2_1);
    myNode2_1.add(myNode2_1_1_notooltip);
  }

  @Test
  public void getTooltipReturnsExpectedValue() throws Exception {
    assertThat(myTooltipLabel.getTooltip(myRootPanel)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1)).isEqualTo("node1");
    assertThat(myTooltipLabel.getTooltip(myNode1_1)).isEqualTo("node1_1");
    assertThat(myTooltipLabel.getTooltip(myNode1_2)).isEqualTo("node1_2");
    assertThat(myTooltipLabel.getTooltip(myNode1_1_1)).isEqualTo("node1_1_1");
    assertThat(myTooltipLabel.getTooltip(myNode1_2_1_notooltip)).isEqualTo("node1_2");
    assertThat(myTooltipLabel.getTooltip(myNode1_2_2_notooltip)).isEqualTo("node1_2");
    assertThat(myTooltipLabel.getTooltip(myNode1_2_2_1_notooltip)).isEqualTo("node1_2");
    assertThat(myTooltipLabel.getTooltip(myNode2)).isEqualTo("node2");
    assertThat(myTooltipLabel.getTooltip(myNode2_1)).isEqualTo("node2_1");
    assertThat(myTooltipLabel.getTooltip(myNode2_1_1_notooltip)).isEqualTo("node2_1");
  }

  @Test
  public void getTooltipReturnsExpectedValueWhenScoped() throws Exception {
    myTooltipLabel.setScope(myNode2);
    assertThat(myTooltipLabel.getTooltip(myRootPanel)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1_1)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1_2)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1_1_1)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1_2_1_notooltip)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1_2_2_notooltip)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode1_2_2_1_notooltip)).isNull();
    assertThat(myTooltipLabel.getTooltip(myNode2)).isEqualTo("node2");
    assertThat(myTooltipLabel.getTooltip(myNode2_1)).isEqualTo("node2_1");
    assertThat(myTooltipLabel.getTooltip(myNode2_1_1_notooltip)).isEqualTo("node2_1");
  }

  @Test
  public void tooltipLabelCantSetTextDirectly() throws Exception {
    String text = myTooltipLabel.getText();
    myTooltipLabel.setText("IGNORED TEXT");
    assertThat(myTooltipLabel.getText()).isEqualTo(text);
  }
}