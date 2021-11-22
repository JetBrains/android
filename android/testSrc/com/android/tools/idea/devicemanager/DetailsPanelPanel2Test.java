/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBScrollPane;
import java.util.Optional;
import javax.swing.JComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DetailsPanelPanel2Test {
  private final JComponent myScrollPane = new JBScrollPane();
  private final DetailsPanelPanel2 myDetailsPanelPanel = new DetailsPanelPanel2(myScrollPane);

  @Test
  public void detailsPanelPanel2() {
    // Assert
    assertArrayEquals(new Object[]{myScrollPane}, myDetailsPanelPanel.getComponents());
  }

  @Test
  public void addSplitter() {
    // Arrange
    DetailsPanel detailsPanel = new DetailsPanel("Device");

    // Act
    myDetailsPanelPanel.addSplitter(detailsPanel);

    // Assert
    Splitter splitter = myDetailsPanelPanel.getSplitter().orElseThrow(AssertionError::new);

    assertEquals(myScrollPane, splitter.getFirstComponent());
    assertEquals(detailsPanel, splitter.getSecondComponent());

    assertArrayEquals(new Object[]{splitter}, myDetailsPanelPanel.getComponents());
  }

  @Test
  public void removeSplitter() {
    // Arrange
    myDetailsPanelPanel.addSplitter(new DetailsPanel("Device"));

    // Act
    myDetailsPanelPanel.removeSplitter();

    // Assert
    assertEquals(Optional.empty(), myDetailsPanelPanel.getDetailsPanel());
    assertEquals(Optional.empty(), myDetailsPanelPanel.getSplitter());

    assertArrayEquals(new Object[]{myScrollPane}, myDetailsPanelPanel.getComponents());
  }
}
