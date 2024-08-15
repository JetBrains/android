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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Objects;

/**
 * Bindings between {@link ConfigurableSetting ConfigurableSettings} and {@link SettingComponent
 * SettingComponents}.
 */
final class SettingComponentBindings {

  private final ImmutableMap<ConfigurableSetting<?, ?>, Binding<?, ?>> settingToBinding;

  private SettingComponentBindings(
      ImmutableMap<ConfigurableSetting<?, ?>, Binding<?, ?>> settingToBinding) {
    this.settingToBinding = settingToBinding;
  }

  static SettingComponentBindings create(List<ConfigurableSetting<?, ?>> settings) {
    return new SettingComponentBindings(Maps.toMap(settings, Binding::new));
  }

  boolean isModified() {
    return settingToBinding.values().stream().anyMatch(Binding::isModified);
  }

  void applySettings() {
    settingToBinding.values().forEach(Binding::applySetting);
  }

  void resetComponents() {
    settingToBinding.values().forEach(Binding::resetComponent);
  }

  // ConfigurableSettings are mapped to the component they create, so the cast here is safe.
  @SuppressWarnings({"unchecked"})
  <C extends SettingComponent<?>> C getComponent(ConfigurableSetting<?, C> setting) {
    checkState(settingToBinding.containsKey(setting), "unknown setting %s", setting);
    return (C) settingToBinding.get(setting).component;
  }

  private static final class Binding<T, C extends SettingComponent<T>> {
    private final Property<T> setting;
    private final C component;

    Binding(ConfigurableSetting<T, C> setting) {
      this.setting = setting.setting();
      this.component = setting.createComponent();
    }

    boolean isModified() {
      return !Objects.equals(component.getValue(), setting.getValue());
    }

    void applySetting() {
      setting.setValue(component.getValue());
    }

    void resetComponent() {
      component.setValue(setting.getValue());
    }
  }
}
