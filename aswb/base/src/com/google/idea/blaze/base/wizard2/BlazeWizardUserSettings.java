/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.wizard2;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import java.util.Map;

/**
 * A bundle of settings that are stored between invocations of the wizard.
 *
 * <p>It's the user's responsibility to appropriately namespace the keys.
 */
public final class BlazeWizardUserSettings {
  Map<String, String> values = Maps.newHashMap();

  public BlazeWizardUserSettings() {}

  public BlazeWizardUserSettings(BlazeWizardUserSettings state) {
    values.putAll(state.getValues());
  }

  public String get(String key, String defaultValue) {
    return values.getOrDefault(key, defaultValue);
  }

  public void put(String key, String value) {
    values.put(key, value);
  }

  @SuppressWarnings("unused")
  @Tag("settings")
  @MapAnnotation(surroundWithTag = false)
  public Map<String, String> getValues() {
    return values;
  }

  @SuppressWarnings("unused")
  public void setValues(Map<String, String> values) {
    this.values = values;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeWizardUserSettings that = (BlazeWizardUserSettings) o;
    return Objects.equal(values, that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(values);
  }
}
