/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionProcessor;
import com.intellij.conversion.ProjectConverter;
import com.intellij.conversion.RunManagerSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * {@link ProjectConverter} to convert {@link com.intellij.execution.junit.JUnitConfiguration}s to {@link AndroidJUnitConfiguration}s.
 * They are converted only in Android Studio.
 */
public class AndroidJUnitConfigurationConverter extends ProjectConverter {
  // Same as RunnerAndConfigurationSettingsImpl.CONFIGURATION_TYPE_ATTRIBUTE
  static final String CONFIGURATION_TYPE_ATTRIBUTE = "type";
  // Same as RunnerAndConfigurationSettingsImpl.FACTORY_NAME_ATTRIBUTE
  static final String FACTORY_NAME_ATTRIBUTE = "factoryName";
  // Same as RunnerAndConfigurationSettingsImpl.TEMPLATE_FLAG_ATTRIBUTE
  static final String TEMPLATE_FLAG_ATTRIBUTE = "default";
  // Same as JUnitConfigurationType#getId()
  static final String JUNIT_CONFIGURATION_TYPE = "JUnit";
  // Same as JUnitConfigurationType#getDisplayName()
  static final String JUNIT_CONFIGURATION_FACTORY_NAME = "JUnit";
  // Same as AndroidJUnitConfigurationType#getId()
  static final String ANDROID_JUNIT_CONFIGURATION_TYPE = "AndroidJUnit";
  // Same as AndroidJUnitConfigurationType#getDisplayName()
  static final String ANDROID_JUNIT_CONFIGURATION_FACTORY_NAME = "Android JUnit";

  @NotNull
  @Override
  public ConversionProcessor<RunManagerSettings> createRunConfigurationsConverter() {
    return new ConversionProcessor<RunManagerSettings>() {
      @Override
      public boolean isConversionNeeded(RunManagerSettings settings) {
        return IdeInfo.getInstance().isAndroidStudio() && !getConfigurationsToConvert(settings).isEmpty();
      }

      @Override
      public void process(RunManagerSettings settings) throws CannotConvertException {
        // Converts the factory identifications from JUnitConfigurationType to AndroidJUnitConfigurationType
        for (Element element : getConfigurationsToConvert(settings)) {
          element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, ANDROID_JUNIT_CONFIGURATION_TYPE);
          element.setAttribute(FACTORY_NAME_ATTRIBUTE, ANDROID_JUNIT_CONFIGURATION_FACTORY_NAME);
        }
      }

      @NotNull
      private Collection<Element> getConfigurationsToConvert(RunManagerSettings settings) {
        Collection<? extends Element> configurationElements = settings.getRunConfigurations();
        Collection<Element> toConvert = new HashSet<>();

        // Searches for RunConfiguration persistent elements of type JUnit and adds them to the ones to be converted
        for (Element element : configurationElements) {
          String typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE);
          String factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE);
          Boolean isTemplate = Boolean.valueOf(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE)).booleanValue();
          if (!isTemplate && typeName.equals(JUNIT_CONFIGURATION_TYPE) && factoryName.equals(JUNIT_CONFIGURATION_FACTORY_NAME)) {
            toConvert.add(element);
          }
        }
        return toConvert;
      }
    };
  }
}
