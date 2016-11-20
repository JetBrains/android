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
package com.android.tools.adtui.visualtests;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.SelectionComponent;
import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectionVisualTest extends VisualTest {

  private SelectionComponent mySelection;

  private Range myRange;

  private Range mySelectionRange;
  private JTextField myRangeMin;
  private JTextField myRangeMax;
  private JTextField mySelectionMin;
  private JTextField mySelectionMax;

  @Override
  protected List<Animatable> createComponentsList() {

    myRange = new Range(0, 1000);
    mySelectionRange = new Range(100, 900);

    mySelection = new SelectionComponent(mySelectionRange, myRange);

    // Add the scene components to the list
    List<Animatable> componentsList = new ArrayList<>();
    componentsList.add(mySelection);
    componentsList.add(frameLength -> {
      myRangeMin.setText(String.valueOf(myRange.getMin()));
      myRangeMax.setText(String.valueOf(myRange.getMax()));
      mySelectionMin.setText(String.valueOf(mySelectionRange.getMin()));
      mySelectionMax.setText(String.valueOf(mySelectionRange.getMax()));
    });

    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Collections.singletonList(mySelection);
  }

  @Override
  public String getName() {
    return "Selection";
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel background = new JPanel(new BorderLayout());
    background.setBackground(Color.WHITE);
    background.add(mySelection, BorderLayout.CENTER);

    JPanel controls = VisualTest.createControlledPane(panel, background);

    myRangeMin = addEntryField("Range Min", controls);
    myRangeMax = addEntryField("Range Max", controls);
    mySelectionMin = addEntryField("Selection Min", controls);
    mySelectionMax = addEntryField("Selection Max", controls);
    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JTextField addEntryField(String name, JPanel controls) {
    JPanel panel = new JPanel(new BorderLayout());
    JTextField field = new JTextField();
    panel.add(new JLabel(name), BorderLayout.WEST);
    panel.add(field);
    controls.add(panel);
    return field;
  }
}
