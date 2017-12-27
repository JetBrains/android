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
package com.android.tools.adtui.visualtests;

import com.android.tools.adtui.FlatTabbedPane;
import com.android.tools.adtui.model.updater.Updatable;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FlatTabbedPaneTest extends VisualTest {

  private static final Map<String, Integer> TAB_PLACEMENT = ImmutableMap.of(
    "Top", SwingConstants.TOP,
    "Left", SwingConstants.LEFT,
    "Bottom", SwingConstants.BOTTOM,
    "Right", SwingConstants.RIGHT
  );

  @Override
  public String getName() {
    return "TabUI";
  }

  @Override
  protected List<Updatable> createModelList() {
    return Collections.emptyList();
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    // Content for tabbed pane.
    FlatTabbedPane tabbedPane = new FlatTabbedPane();
    for (int i = 0; i < 5; i++) {
      tabbedPane.add("Tab" + i, new JLabel("Content " + i));
    }

    JPanel controls = VisualTest.createControlledPane(panel, tabbedPane);
    final JComboBox comboBox = new ComboBox();
    TAB_PLACEMENT.keySet().forEach(placement -> comboBox.addItem(placement));
    comboBox.addActionListener(e -> tabbedPane.setTabPlacement(TAB_PLACEMENT.get((String)comboBox.getSelectedItem())));
    comboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    controls.add(comboBox);

    createSlidersForInsets(controls, tabbedPane, tabbedPane.getTabAreaInsets(), "tabAreaInsets", tabbedPane::setTabAreaInsets);
    createSlidersForInsets(controls, tabbedPane, tabbedPane.getTabInsets(), "tabInsets", tabbedPane::setTabInsets);
    createSlidersForInsets(controls, tabbedPane, tabbedPane.getContentBorderInsets(), "contentBorderInsets",
                           tabbedPane::setContentBorderInsets);

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(100, Integer.MAX_VALUE),
                     new Dimension(100, Integer.MAX_VALUE)));
  }

  private void createSlidersForInsets(@NotNull JPanel controls,
                                      @NotNull FlatTabbedPane tabbedPane,
                                      @NotNull Insets insets,
                                      @NotNull String insetsName,
                                      @NotNull Consumer<Insets> updateFunc) {
    controls.add(VisualTest.createVariableSlider(insetsName + "Top", -10, 10, new VisualTests.Value() {
      @Override
      public void set(int v) {
        insets.top = v;
        updateFunc.accept(insets);
      }

      @Override
      public int get() {
        return insets.top;
      }
    }));

    controls.add(VisualTest.createVariableSlider(insetsName + "Left", -10, 10, new VisualTests.Value() {
      @Override
      public void set(int v) {
        insets.left = v;
        updateFunc.accept(insets);
      }

      @Override
      public int get() {
        return insets.left;
      }
    }));

    controls.add(VisualTest.createVariableSlider(insetsName + "Bottom", -10, 10, new VisualTests.Value() {
      @Override
      public void set(int v) {
        insets.bottom = v;
        updateFunc.accept(insets);
      }

      @Override
      public int get() {
        return insets.bottom;
      }
    }));

    controls.add(VisualTest.createVariableSlider(insetsName + "Right", -10, 10, new VisualTests.Value() {
      @Override
      public void set(int v) {
        insets.right = v;
        updateFunc.accept(insets);
      }

      @Override
      public int get() {
        return insets.right;
      }
    }));
  }
}
