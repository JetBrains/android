/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.flags;

import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.rendering.RenderSettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.PlatformTestCase;
import org.mockito.Mock;

/**
 * Tests for {@link ExperimentalSettingsConfigurable}.
 */
public class ExperimentalSettingsConfigurableTest extends PlatformTestCase {
  @Mock private GradleExperimentalSettings mySettings;
  private ExperimentalSettingsConfigurable myConfigurable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myConfigurable = new ExperimentalSettingsConfigurable(mySettings, new RenderSettings());
  }

  public void testIsModified() {
    myConfigurable.setUseL2DependenciesInSync(true);
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setUseSingleVariantSync(true);
    mySettings.USE_SINGLE_VARIANT_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_SINGLE_VARIANT_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setUseNewPsd(true);
    mySettings.USE_NEW_PSD = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_NEW_PSD = true;
    assertFalse(myConfigurable.isModified());
  }

  public void testApply() throws ConfigurationException {
    myConfigurable.setUseL2DependenciesInSync(true);
    myConfigurable.setUseSingleVariantSync(true);
    myConfigurable.setUseNewPsd(true);

    myConfigurable.apply();

    assertTrue(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
    assertTrue(mySettings.USE_SINGLE_VARIANT_SYNC);
    assertTrue(mySettings.USE_NEW_PSD);

    myConfigurable.setUseL2DependenciesInSync(false);
    myConfigurable.setUseSingleVariantSync(false);
    myConfigurable.setUseNewPsd(false);

    myConfigurable.apply();

    assertFalse(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
    assertFalse(mySettings.USE_SINGLE_VARIANT_SYNC);
    assertFalse(mySettings.USE_NEW_PSD);
  }

  public void testReset() {
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = true;
    mySettings.USE_SINGLE_VARIANT_SYNC = true;
    mySettings.USE_NEW_PSD = true;

    myConfigurable.reset();

    assertTrue(myConfigurable.isUseL2DependenciesInSync());
    assertTrue(myConfigurable.isUseSingleVariantSync());
    assertTrue(myConfigurable.isUseNewPsd());

    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = false;
    mySettings.USE_SINGLE_VARIANT_SYNC = false;
    mySettings.USE_NEW_PSD = false;

    myConfigurable.reset();

    assertFalse(myConfigurable.isUseL2DependenciesInSync());
    assertFalse(myConfigurable.isUseSingleVariantSync());
    assertFalse(myConfigurable.isUseNewPsd());
  }
}