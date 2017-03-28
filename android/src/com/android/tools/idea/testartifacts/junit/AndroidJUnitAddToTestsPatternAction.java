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

import com.intellij.execution.actions.AbstractAddToTestsPatternAction;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.JUnitConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AndroidJUnitAddToTestsPatternAction extends AbstractAddToTestsPatternAction<AndroidJUnitConfiguration> {

  @Override
  @NotNull
  protected TestPatternConfigurationProducer getPatternBasedProducer() {
    return RunConfigurationProducer.getInstance(TestPatternConfigurationProducer.class);
  }

  @Override
  @NotNull
  protected ConfigurationType getConfigurationType() {
    return AndroidJUnitConfigurationType.getInstance();
  }

  @Override
  protected boolean isPatternBasedConfiguration(AndroidJUnitConfiguration configuration) {
    return configuration.getPersistentData().TEST_OBJECT == JUnitConfiguration.TEST_PATTERN;
  }

  @Override
  protected Set<String> getPatterns(AndroidJUnitConfiguration configuration) {
    return configuration.getPersistentData().getPatterns();
  }
}