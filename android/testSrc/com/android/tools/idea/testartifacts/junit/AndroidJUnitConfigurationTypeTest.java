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

import com.intellij.execution.junit.JUnitConfigurationType;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.junit.Ignore;

/**
 * Tests for {@link AndroidJUnitConfigurationType}
 */
@Ignore // Broken after 2017.1 merge
public class AndroidJUnitConfigurationTypeTest extends AndroidTestCase {

  public void testConfigurationTypeIdRegistered() {
    assertTrue(AndroidCommonUtils.isTestConfiguration(AndroidJUnitConfigurationType.getInstance().getId()));
  }

  // Since JUnitConfigurationType is disabled in AndroidStudioInitializer, and AndroidJUnitConfigurationType
  // is registered as child, it should be returned when getInstance() of the parent is called
  public void testJUnitConfigurationTypeGetInstance() {
    JUnitConfigurationType configurationType = JUnitConfigurationType.getInstance();
    assertInstanceOf(configurationType, AndroidJUnitConfigurationType.class);
  }
}
