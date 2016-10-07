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

package com.android.tools.adtui.visual;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * Represent a Visual Test, containing a group of UI components, including charts. Classes
 * inheriting {@code VisualTest} must implement the abstract methods according to documentation in
 * order to work properly.
 */
public abstract class VisualTest {

  private static final int CHOREOGRAPHER_FPS = 60;

  /**
   * Main panel of the VisualTest, which contains all the other elements.
   */
  private JBPanel mPanel;

  private Choreographer mChoreographer;

  /**
   * Thread to be used to update components data. If set, it is going to be interrupted in {@code
   * reset}. Note that if the subclass creates some other threads, it should be responsible for
   * keeping track and interrupting them when necessary.
   */
  public JPanel getPanel() {
    return mPanel;
  }

  protected final Choreographer getChoreographer() {
    return mChoreographer;
  }

  /**
   * The {@code Animatable} components should be created in this method.
   *
   * @return An ordered {@code List} containing the Animatables which should be added to the
   * {@link Choreographer} of this {@link VisualTest}.
   */
  protected abstract List<Animatable> createComponentsList();

  protected final void addToChoreographer(@NotNull Animatable animatable) {
    mChoreographer.register(animatable);
  }

  protected final void addToChoreographer(@NotNull List<Animatable> animatables) {
    mChoreographer.register(animatables);
  }

  /**
   * The UI elements for the test should be populated inside {@code panel}. It can use elements
   * created in {@code createComponentsList}.
   */
  protected abstract void populateUi(@NotNull JPanel panel);

  protected void initialize() {
    mPanel = new JBPanel();
    mChoreographer = new Choreographer(CHOREOGRAPHER_FPS, mPanel);
    mChoreographer.register(createComponentsList());
    populateUi(mPanel);
  }

  /**
   * Interrupt active threads, clear all the components of the test and initialize it again.
   */
  protected void reset() {
    initialize();
  }

  public abstract String getName();

  protected static JPanel createVariableSlider(String name, final int a, final int b, final VisualTests.Value value) {
    JPanel panel = new JPanel(new BorderLayout());
    final JLabel text = new JLabel();
    final JSlider slider = new JSlider(a, b);
    ChangeListener listener = changeEvent -> {
      value.set(slider.getValue());
      text.setText(String.format("%d [%d,%d]", slider.getValue(), a, b));
    };
    slider.setValue(value.get());
    listener.stateChanged(null);
    slider.addChangeListener(listener);
    panel.add(slider, BorderLayout.CENTER);
    panel.add(new JLabel(name + ": "), BorderLayout.WEST);
    panel.add(text, BorderLayout.EAST);
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    return panel;
  }

  protected static JPanel createControlledPane(JPanel panel, Component animated) {
    panel.setLayout(new BorderLayout());
    panel.add(animated, BorderLayout.CENTER);

    JPanel controls = new JPanel();
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);
    return controls;
  }

  protected static Component createButton(String label, ActionListener action) {
    JButton button = createButton(label);
    button.addActionListener(action);
    return button;
  }

  protected static Component createCheckbox(String label, ItemListener action) {
    return createCheckbox(label, action, false);
  }

  protected static Component createCheckbox(String label, ItemListener action, boolean selected) {
    JCheckBox button = new JCheckBox(label);
    button.addItemListener(action);
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getMaximumSize().height));
    button.setSelected(selected);
    return button;
  }

  protected static JButton createButton(String label) {
    JButton button = new JButton(label);
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getMaximumSize().height));
    return button;
  }
}
