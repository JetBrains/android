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
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.conversion.ConversionProcessor;
import com.intellij.conversion.RunManagerSettings;
import com.intellij.testFramework.PlatformTestCase;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import static com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationConverter.*;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_OLD_TESTS;
import static org.jetbrains.android.AndroidTestBase.getTestDataPath;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AndroidJUnitConfigurationConverter}.
 */
public class AndroidJUnitConfigurationConverterTest extends PlatformTestCase {
  private IdeComponents myIdeComponents;
  private IdeInfo mockIdeInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(myProject);
    mockIdeInfo = myIdeComponents.mockService(IdeInfo.class);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testConfigurationsAreConvertedInStudio() throws Exception {
    when(mockIdeInfo.isAndroidStudio()).thenReturn(true);

    ConversionProcessor<RunManagerSettings> converter = new AndroidJUnitConfigurationConverter().createRunConfigurationsConverter();
    RunManagerSettings runManagerSettings = getStubRunManagerSettings();
    Collection<? extends Element> runConfigurations = runManagerSettings.getRunConfigurations();

    assertTrue(converter.isConversionNeeded(runManagerSettings));
    assertSize(2, getJUnitConfigurations(runConfigurations));
    assertEmpty(getAndroidJUnitConfigurations(runConfigurations));

    converter.process(runManagerSettings);
    assertEmpty(getJUnitConfigurations(runConfigurations));
    assertSize(2, getAndroidJUnitConfigurations(runConfigurations));
  }

  public void testConfigurationsAreNotConvertedInIdea() {
    when(mockIdeInfo.isAndroidStudio()).thenReturn(false);

    ConversionProcessor<RunManagerSettings> converter = new AndroidJUnitConfigurationConverter().createRunConfigurationsConverter();
    RunManagerSettings runManagerSettings = getStubRunManagerSettings();

    assertFalse(converter.isConversionNeeded(runManagerSettings));
  }

  @NotNull
  private static RunManagerSettings getStubRunManagerSettings() {
    return new RunManagerSettings() {
      Collection<Element> myElements = null;
      @NotNull
      @Override
      public Collection<? extends Element> getRunConfigurations() {
        if (myElements == null) {
          Element root = null;
          try {
            root = readElement(getTestDataPath() + "/" + TEST_ARTIFACTS_OLD_TESTS + "/.idea/workspace.xml");
          }
          catch (Exception e) {
            e.printStackTrace();
          }
          myElements = root != null ? root.getChild("component").getChildren("configuration") : Collections.emptySet();
        }
        return myElements;
      }
    };
  }

  private static Element readElement(String path) throws Exception {
    return new SAXBuilder().build(new File(path)).getRootElement();
  }

  @NotNull
  private static Collection<Element> getJUnitConfigurations(@NotNull Collection<? extends Element> configurations) {
    return getConfigurationsOfType(configurations, JUNIT_CONFIGURATION_TYPE, JUNIT_CONFIGURATION_FACTORY_NAME);
  }

  @NotNull
  private static Collection<Element> getAndroidJUnitConfigurations(@NotNull Collection<? extends Element> configurations) {
    return getConfigurationsOfType(configurations, ANDROID_JUNIT_CONFIGURATION_TYPE, ANDROID_JUNIT_CONFIGURATION_FACTORY_NAME);
  }

  @NotNull
  private static Collection<Element> getConfigurationsOfType(@NotNull Collection<? extends Element> configurations,
                                                             @NotNull String configurationType,
                                                             @NotNull String configurationFactoryName) {
    Collection<Element> aimConfigurations = new LinkedList<>();
    for (Element element : configurations) {
      String typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE);
      String factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE);
      Boolean isTemplate = Boolean.valueOf(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE)).booleanValue();
      if (!isTemplate && typeName.equals(configurationType) && factoryName.equals(configurationFactoryName)) {
        aimConfigurations.add(element);
      }
    }
    return aimConfigurations;
  }
}
