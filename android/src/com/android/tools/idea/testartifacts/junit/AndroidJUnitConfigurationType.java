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

import com.android.tools.idea.IdeInfo;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.LazyUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Android implementation of {@link JUnitConfigurationType} for running local unit tests. Dual test scopes is supported.
 */
public class AndroidJUnitConfigurationType extends ConfigurationTypeBase {
  private static final String ANDROID_JUNIT_DESCRIPTION = "Android JUnit test configuration";
  private static final String ANDROID_JUNIT_NAME = "Android JUnit";
  private static final String ANDROID_JUNIT_ID = "AndroidJUnit";
  private static final NotNullLazyValue<Icon> ANDROID_TEST_ICON = new NotNullLazyValue<Icon>() {
    @NotNull
    @Override
    protected Icon compute() {
      LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(AndroidIcons.AndroidModule, 0);
      icon.setIcon(AllIcons.Nodes.JunitTestMark, 1);
      return icon;
    }
  };

  public AndroidJUnitConfigurationType() {
    super(ANDROID_JUNIT_ID, ANDROID_JUNIT_NAME, ANDROID_JUNIT_DESCRIPTION, LazyUtil.create(() -> IdeInfo.getInstance().isAndroidStudio() ? AllIcons.RunConfigurations.Junit : ANDROID_TEST_ICON.getValue()));

    addFactory(new ConfigurationFactory(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        AndroidJUnitConfiguration configuration = new AndroidJUnitConfiguration("", project, this);
        configuration.setVMParameters("-ea");
        configuration.setWorkingDirectory("$MODULE_DIR$");
        return configuration;
      }
    });
  }

  @NotNull
  public static AndroidJUnitConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidJUnitConfigurationType.class);
  }

  @NotNull
  @Override
  public String getConfigurationPropertyName() {
    return "androidJunit";
  }
}
