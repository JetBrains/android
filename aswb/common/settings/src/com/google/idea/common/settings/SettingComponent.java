/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.settings;

import com.google.idea.common.settings.ConfigurableSetting.ComponentFactory;
import com.google.idea.common.settings.Property.Getter;
import com.google.idea.common.settings.Property.Setter;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.SwingHelper;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** A UI component for representing a setting in an {@link AutoConfigurable}. */
public abstract class SettingComponent<T> {

  /**
   * Returns the root Swing component.
   *
   * <p>If necessary, a root component will be created to wrap any subcomponents. The same root
   * component will be returned on subsequent calls.
   *
   * <p><strong>Warning:</strong> if you intend to insert individual subcomponents directly into a
   * layout, do not call this method. You won't need a root anyhow, and creating one will re-parent
   * the subcomponents to it.
   */
  public abstract JComponent getRootComponent();

  /**
   * Sets whether the component is enabled and visible.
   *
   * <p>Use this method instead of making the root component invisible directly. Otherwise, some
   * subcomponents might remain enabled and break tab navigation.
   */
  public abstract void setEnabledAndVisible(boolean visible);

  /** Returns a {@link Property} for the value of the UI component. */
  abstract Property<T> getProperty();

  /** Returns the value of the UI component. */
  public final T getValue() {
    return getProperty().getValue();
  }

  /** Sets the value of the UI component. */
  public final void setValue(T value) {
    getProperty().setValue(value);
  }

  /** A {@link SettingComponent} for a single Swing component. */
  public static final class SimpleComponent<T, C extends JComponent> extends SettingComponent<T> {

    /**
     * Creates a {@link SimpleComponent} for the given Swing component.
     *
     * @param getter a {@link Getter} for accessing the component's value
     * @param setter a {@link Setter} for modifying the component's value
     */
    public static <T, C extends JComponent> SimpleComponent<T, C> create(
        C component, Getter<C, T> getter, Setter<C, T> setter) {

      Property<T> property = Property.create(() -> component, getter, setter);
      return new SimpleComponent<>(component, property);
    }

    /** Creates a boolean {@link SettingComponent} for a {@link JBCheckBox}. */
    public static SimpleComponent<Boolean, JBCheckBox> createCheckBox(String label) {
      return create(new JBCheckBox(label), JCheckBox::isSelected, JCheckBox::setSelected);
    }

    private final C component;
    private final Property<T> property;

    private SimpleComponent(C component, Property<T> property) {
      this.component = component;
      this.property = property;
    }

    /** Returns the Swing component directly. */
    @Override
    public C getRootComponent() {
      return component;
    }

    @Override
    public void setEnabledAndVisible(boolean visible) {
      component.setVisible(visible);
      component.setEnabled(visible);
    }

    @Override
    Property<T> getProperty() {
      return property;
    }
  }

  /** A {@link SettingComponent} for a Swing component and its label. */
  public static final class LabeledComponent<T, C extends JComponent> extends SettingComponent<T> {

    /**
     * Creates a {@link LabeledComponent} for the given label text and Swing component.
     *
     * @param getter a {@link Getter} for accessing the component's value
     * @param setter a {@link Setter} for modifying the component's value
     */
    public static <T, C extends JComponent> LabeledComponent<T, C> create(
        String label, C component, Getter<C, T> getter, Setter<C, T> setter) {

      JBLabel labelComponent = new JBLabel(label);
      labelComponent.setLabelFor(component);
      Property<T> property = Property.create(() -> component, getter, setter);

      return new LabeledComponent<>(labelComponent, component, property);
    }

    /**
     * Creates a factory for producing {@link LabeledComponent LabeledComponents}.
     *
     * @param jComponentFactory a factory for creating the Swing component
     * @param getter a {@link Getter} for accessing the component's value
     * @param setter a {@link Setter} for modifying the component's value
     */
    public static <T, C extends JComponent> ComponentFactory<LabeledComponent<T, C>> factory(
        Supplier<C> jComponentFactory, Getter<C, T> getter, Setter<C, T> setter) {
      return label -> create(label, jComponentFactory.get(), getter, setter);
    }

    /** Creates a factory for {@link ComboBox} components for selecting enum values. */
    public static <E extends Enum<E>>
        ComponentFactory<LabeledComponent<E, ComboBox<E>>> comboBoxFactory(Class<E> optionsEnum) {
      return factory(
          () -> new ComboBox<>(optionsEnum.getEnumConstants()),
          c -> c.getItemAt(c.getSelectedIndex()),
          JComboBox::setSelectedItem);
    }

    /** Creates a factory for plain {@link JTextField} components. */
    public static ComponentFactory<LabeledComponent<String, JTextField>> textFieldFactory() {
      return factory(JTextField::new, JTextField::getText, JTextField::setText);
    }

    private final JBLabel label;
    private final C component;
    private final Property<T> property;

    private JPanel rootPanel;

    private LabeledComponent(JBLabel label, C component, Property<T> property) {
      this.label = label;
      this.component = component;
      this.property = property;
    }

    /** Returns the label subcomponent. */
    public JBLabel getLabel() {
      return label;
    }

    /** Returns the value subcomponent. */
    public C getComponent() {
      return component;
    }

    @Override
    public JComponent getRootComponent() {
      if (rootPanel == null) {
        rootPanel =
            SwingHelper.newHorizontalPanel(
                Component.CENTER_ALIGNMENT,
                label,
                Box.createRigidArea(new Dimension(5, 0)),
                component);
        rootPanel.setVisible(component.isVisible());
      }
      return rootPanel;
    }

    @Override
    public void setEnabledAndVisible(boolean visible) {
      if (rootPanel != null) {
        rootPanel.setVisible(visible);
      }
      label.setVisible(visible);
      component.setVisible(visible);

      component.setEnabled(visible);
    }

    @Override
    Property<T> getProperty() {
      return property;
    }
  }
}
