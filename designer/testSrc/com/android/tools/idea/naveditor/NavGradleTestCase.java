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
package com.android.tools.idea.naveditor;

import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.naveditor.scene.TestableThumbnailManager;
import com.android.tools.idea.naveditor.scene.ThumbnailManager;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_BASIC;

/**
 * Base class for navigation editor tests that require a full gradle project.
 * In most cases this shouldn't be necessary, and extending NavigationTestCase will result in tests that run much faster.
 * Only extend this one if you're testing something more deeply involved with the building of a complete project.
 */
public abstract class NavGradleTestCase extends AndroidGradleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject(NAVIGATION_EDITOR_BASIC);
    generateSources();
    myFixture.setTestDataPath(getTestDataPath());
    TestableThumbnailManager.register(myAndroidFacet);
    System.setProperty(NavigationSchema.ENABLE_NAV_PROPERTY, "true");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ThumbnailManager thumbnailManager = ThumbnailManager.getInstance(myAndroidFacet);
      if (thumbnailManager instanceof TestableThumbnailManager) {
        ((TestableThumbnailManager)thumbnailManager).deregister();
      }
    }
    finally {
      super.tearDown();
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static String getTestDataPath() {
    return getDesignerPluginHome() + "/testData";
  }

  @NotNull
  private static String getDesignerPluginHome() {
    // Now that the Android plugin is kept in a separate place, we need to look in
    // a relative position instead
    String adtPath = PathManager.getHomePath() + "/../adt/idea/designer";
    if (new File(adtPath).exists()) {
      return adtPath;
    }
    return AndroidTestBase.getAndroidPluginHome();
  }

  @NotNull
  protected ModelBuilder model(@NotNull String name, @NotNull ComponentDescriptor root) {
    return NavModelBuilderUtil.model(name, root, myAndroidFacet, myFixture);
  }
}
