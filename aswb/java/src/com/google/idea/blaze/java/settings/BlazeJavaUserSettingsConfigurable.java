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
package com.google.idea.blaze.java.settings;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsCompositeConfigurable;
import com.google.idea.common.settings.AutoConfigurable;
import com.google.idea.common.settings.ConfigurableSetting;
import com.google.idea.common.settings.SearchableText;
import com.google.idea.common.settings.SettingComponent.SimpleComponent;
import com.intellij.openapi.options.UnnamedConfigurable;

/** Contributes java-specific settings. */
class BlazeJavaUserSettingsConfigurable extends AutoConfigurable {

  static class UiContributor implements BlazeUserSettingsCompositeConfigurable.UiContributor {
    @Override
    public UnnamedConfigurable getConfigurable() {
      return new BlazeJavaUserSettingsConfigurable();
    }

    @Override
    public ImmutableCollection<SearchableText> getSearchableText() {
      return SearchableText.collect(SETTINGS);
    }
  }

  private static final ConfigurableSetting<?, ?> USE_JAR_CACHE =
      ConfigurableSetting.builder(BlazeJavaUserSettings::getInstance)
          .label(
              String.format(
                  "Use a local jar cache."
                      + " More robust, but we can miss %s changes made outside the IDE.",
                  Blaze.defaultBuildSystemName()))
          .getter(BlazeJavaUserSettings::getUseJarCache)
          .setter(BlazeJavaUserSettings::setUseJarCache)
          .hideIf(() -> !BlazeJavaUserSettings.allowJarCache())
          .componentFactory(SimpleComponent::createCheckBox);

  private static final ImmutableList<ConfigurableSetting<?, ?>> SETTINGS =
      ImmutableList.of(USE_JAR_CACHE);

  private BlazeJavaUserSettingsConfigurable() {
    super(SETTINGS);
  }

}
