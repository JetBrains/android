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

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.BoxSelectionComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.BoxSelectionModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;
import com.intellij.ui.components.JBList;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

public class BoxSelectionVisualTest extends VisualTest {

  private BoxSelectionComponent mySelection;

  private Range myViewRange;

  private Range mySelectionRange;
  private JTextField myRangeMin;
  private JTextField myRangeMax;
  private JTextField mySelectionMin;
  private JTextField mySelectionMax;
  private JPanel myComponent;

  @Override
  protected List<Updatable> createModelList() {
    myViewRange = new Range(0, 1000);
    mySelectionRange = new Range(100, 900);
    BoxSelectionModel rangeSelectionModel = new BoxSelectionModel(mySelectionRange, myViewRange);
    JList<String> JList = new JBList<>(generateJListData(10));
    JList.setSelectionForeground(Color.BLACK);
    JList.setSelectionBackground(Color.WHITE);
    mySelection = new BoxSelectionComponent(rangeSelectionModel, JList);

    // Add the scene components to the list
    List<Updatable> componentsList = new ArrayList<>();
    componentsList.add(frameLength -> {
      myRangeMin.setText(String.valueOf(myViewRange.getMin()));
      myRangeMax.setText(String.valueOf(myViewRange.getMax()));
      mySelectionMin.setText(String.valueOf(mySelectionRange.getMin()));
      mySelectionMax.setText(String.valueOf(mySelectionRange.getMax()));
    });

    myComponent = new JPanel(new TabularLayout("50px,*", "Fit"));
    myComponent.setBackground(Color.WHITE);
    myComponent.add(mySelection, new TabularLayout.Constraint(0, 1));
    myComponent.add(JList, new TabularLayout.Constraint(0, 0, 1, 2));
    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Collections.singletonList(mySelection);
  }

  @Override
  public String getName() {
    return "Box Selection";
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel controls = VisualTest.createControlledPane(panel, myComponent);

    myRangeMin = addEntryField("Range Min", controls);
    myRangeMax = addEntryField("Range Max", controls);
    mySelectionMin = addEntryField("Selection Min", controls);
    mySelectionMax = addEntryField("Selection Max", controls);
    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private static JTextField addEntryField(String name, JPanel controls) {
    JPanel panel = new JPanel(new BorderLayout());
    JTextField field = new JTextField();
    panel.add(new JLabel(name), BorderLayout.WEST);
    panel.add(field);
    controls.add(panel);
    return field;
  }

  private static List<String> generateJListData(int size) {
    List<String> data = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      data.add("Item " + i);
    }
    return data;
  }
}
