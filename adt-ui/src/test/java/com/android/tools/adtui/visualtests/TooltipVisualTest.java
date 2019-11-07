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
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.DefaultTimeline;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.updater.Updatable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class TooltipVisualTest extends VisualTest {

  private RangeTooltipComponent myTooltip;

  private Timeline myTimeline = new DefaultTimeline();
  private JPanel myContent;
  private JLabel myLabel;

  @Override
  protected List<Updatable> createModelList() {
    myTimeline.getViewRange().set(0, 1000);
    myTimeline.getDataRange().set(250, 1000);
    myTimeline.getTooltipRange().set(500, 500);

    myContent = new JPanel(new BorderLayout());
    myTooltip = new RangeTooltipComponent(myTimeline, myContent);

    List<Updatable> componentsList = new ArrayList<>();
    componentsList.add(t -> myLabel.setText(String.valueOf(System.nanoTime())));
    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Collections.singletonList(myTooltip);
  }

  @Override
  public String getName() {
    return "Tooltip";
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel container = new JPanel(new TabularLayout("*", "*"));
    JPanel background = new JPanel(new BorderLayout());
    CheckeredComponent center = new CheckeredComponent();
    background.add(center, BorderLayout.CENTER);
    background.add(new JLabel("NORTH"), BorderLayout.NORTH);
    background.add(new JLabel("SOUTH"), BorderLayout.SOUTH);
    JLabel east = new JLabel("EAST");

    background.add(east, BorderLayout.EAST);
    background.add(new JLabel("WEST"), BorderLayout.WEST);

    myTooltip.registerListenersOn(center);
    myTooltip.registerListenersOn(east);

    container.add(myTooltip, new TabularLayout.Constraint(0, 0));
    container.add(background, new TabularLayout.Constraint(0, 0));

    JPanel controls = VisualTest.createControlledPane(panel, container);
    controls.add(VisualTest.createVariableSlider("Highlight", 1, 1000, new VisualTests.Value() {
      @Override
      public void set(int v) {
        myTimeline.getTooltipRange().set(v, v);
      }

      @Override
      public int get() {
        return (int)myTimeline.getTooltipRange().getMin();
      }
    }));

    myLabel = new JLabel();
    controls.add(VisualTest.createButton("Small", e -> setContent(myLabel)));
    controls.add(VisualTest.createButton("Big", this::createBigContent));
    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private void createBigContent(ActionEvent event) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel("CENTER"), BorderLayout.CENTER);
    panel.add(new JLabel("NORTH"), BorderLayout.NORTH);
    panel.add(new JLabel("SOUTH"), BorderLayout.SOUTH);
    panel.add(new JLabel("EAST"), BorderLayout.EAST);
    panel.add(new JLabel("WEST"), BorderLayout.WEST);
    setContent(panel);
  }

  public void setContent(JComponent content) {
    myContent.removeAll();
    myContent.add(content, BorderLayout.CENTER);
    myTooltip.repaint();
  }

  private static class CheckeredComponent extends JComponent {
    @Override
    public void paint(Graphics g) {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(Color.LIGHT_GRAY);
      int size = 50;
      for (int i = 0; i < getWidth() / size + 1; i++) {
        for (int j = 0; j < getHeight() / size + 1; j++) {
          if ((i + j) % 2 == 0) {
            int width = Math.min(size, getWidth() - i * size);
            int height = Math.min(size, getHeight() - j * size);
            g.fillRect(i * size, j * size, width, height);
          }
        }
      }
    }
  }
}
