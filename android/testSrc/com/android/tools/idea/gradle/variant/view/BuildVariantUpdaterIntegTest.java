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
package com.android.tools.idea.gradle.variant.view;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;

public class BuildVariantUpdaterIntegTest extends AndroidGradleTestCase {

  public void testUpdateVariantForFeatureModule() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
    AndroidModuleModel featureAndroidModel = AndroidModuleModel.get(getModule("feature1"));
    assertNotNull(appAndroidModel);
    assertNotNull(featureAndroidModel);
    assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
    assertEquals("debug", featureAndroidModel.getSelectedVariant().getName());

    BuildVariantUpdater.getInstance(getProject()).updateSelectedVariant(getProject(), "app", "release");

    assertEquals("release", appAndroidModel.getSelectedVariant().getName());
    assertEquals("release", featureAndroidModel.getSelectedVariant().getName());
  }
}
