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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.uiDesigner.core.AbstractLayout;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A utility for implementing a {@linkplain UnnamedConfigurable configurable} declaratively via
 * {@link ConfigurableSetting ConfigurableSettings}.
 */
public abstract class AutoConfigurable implements UnnamedConfigurable {

  private final ImmutableList<ConfigurableSetting<?, ?>> settings;

  private SettingComponentBindings bindings;

  /** Creates an {@link AutoConfigurable} for the given settings. */
  protected AutoConfigurable(List<? extends ConfigurableSetting<?, ?>> settings) {
    this.settings = ImmutableList.copyOf(settings);
  }

  private SettingComponentBindings getBindings() {
    if (bindings == null) {
      bindings = SettingComponentBindings.create(settings);
    }
    return bindings;
  }

  /**
   * Returns the {@link SettingComponent} corresponding to the given setting.
   *
   * @throws IllegalStateException if {@code setting} was not a {@link ConfigurableSetting} instance
   *     passed to the constructor of this {@link AutoConfigurable}.
   */
  protected <C extends SettingComponent<?>> C getComponent(ConfigurableSetting<?, C> setting) {
    return getBindings().getComponent(setting);
  }

  /**
   * Returns the root Swing component corresponding to the given setting.
   *
   * <p><strong>Warning:</strong> if you intend to insert individual subcomponents directly into a
   * layout, do not call this method. You won't need a root anyhow, and creating one will re-parent
   * the subcomponents to it.
   *
   * @throws IllegalStateException if {@code setting} was not a {@link ConfigurableSetting} instance
   *     passed to the constructor of this {@link AutoConfigurable}.
   */
  protected JComponent getRootComponent(ConfigurableSetting<?, ?> setting) {
    return getBindings().getComponent(setting).getRootComponent();
  }

  /**
   * Creates a panel containing the root Swing components of the given settings, stacked vertically.
   *
   * @throws IllegalStateException if any setting was not a {@link ConfigurableSetting} instance
   *     passed to the constructor of this {@link AutoConfigurable}.
   */
  protected JPanel createVerticalPanel(ConfigurableSetting<?, ?>... settings) {
    return createVerticalPanel(ImmutableList.copyOf(settings));
  }

  /**
   * Creates a panel containing the root Swing components of the given settings, stacked vertically.
   *
   * @throws IllegalStateException if any setting was not a {@link ConfigurableSetting} instance
   *     passed to the constructor of this {@link AutoConfigurable}.
   */
  protected JPanel createVerticalPanel(List<ConfigurableSetting<?, ?>> settings) {
    JPanel panel = new JPanel(new VerticalLayout(/* gap= */ AbstractLayout.DEFAULT_VGAP));
    settings.stream().map(this::getRootComponent).forEach(panel::add);
    return panel;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return createVerticalPanel(settings);
  }

  @Override
  public boolean isModified() {
    return getBindings().isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    getBindings().applySettings();
  }

  @Override
  public void reset() {
    getBindings().resetComponents();
  }
}
