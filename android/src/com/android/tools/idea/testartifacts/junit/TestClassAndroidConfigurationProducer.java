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
package com.android.tools.idea.testartifacts.junit;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurations.isFromContext;
import static com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurations.shouldUseAndroidJUnitConfigurations;

/**
 * Android implementation of {@link AbstractInClassConfigurationProducer} so some behaviors can be overridden.
 */
final class TestClassAndroidConfigurationProducer extends AbstractInClassConfigurationProducer<JUnitConfiguration> implements AndroidJUnitConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return AndroidJUnitConfigurationType.getInstance();
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return super.isPreferredConfiguration(self, other)
           && shouldUseAndroidJUnitConfigurations(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return super.isPreferredConfiguration(self, other)
           && shouldUseAndroidJUnitConfigurations(self, other);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull JUnitConfiguration unitConfiguration, @NotNull ConfigurationContext context) {
    return isFromContext(unitConfiguration, context, getConfigurationFactory());
  }
}
