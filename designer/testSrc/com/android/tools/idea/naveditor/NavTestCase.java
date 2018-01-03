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
import com.android.tools.idea.startup.AndroidCodeStyleSettingsModifier;
import com.google.common.base.Preconditions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_BASIC;

public abstract class NavTestCase extends AndroidTestCase {

  protected static final String TAG_NAVIGATION = "navigation";
  private static final String[] PREBUILT_AAR_PATHS = {
    "../../prebuilts/tools/common/m2/repository/android/arch/navigation/runtime/0.6.0-alpha1/runtime-0.6.0-alpha1.aar",
    "../../prebuilts/tools/common/m2/repository/com/android/support/support-fragment/27.0.2/support-fragment-27.0.2.aar"
  };
  private CodeStyleSettings mySettings;
  private boolean myUseCustomSettings;

  // The normal test root disposable is disposed after Timer leak checking is done, which can cause problems.
  // We'll dispose this one first, so it should be used instead of getTestRootDisposable().
  protected Disposable myRootDisposable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    //noinspection Convert2Lambda
    myRootDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };
    myFixture.copyDirectoryToProject(NAVIGATION_EDITOR_BASIC + "/app/src/main/java", "src");
    myFixture.copyDirectoryToProject(NAVIGATION_EDITOR_BASIC + "/app/src/main/res", "res");
    myFixture.copyFileToProject(NAVIGATION_EDITOR_BASIC + "/app/src/main/AndroidManifest.xml", "AndroidManifest.xml");
    File tempDir = FileUtil.createTempDirectory("NavigationTest", null);
    int i = 0;
    File classesDir = FileUtil.createTempDirectory("NavigationTestClasses", null);
    for (String prebuilt : PREBUILT_AAR_PATHS) {
      File aar = new File(PathManager.getHomePath(), prebuilt);
      ZipUtil.extract(aar, tempDir, null);
      File classes = new File(classesDir, "classes" + i + ".jar");
      Preconditions.checkState(new File(tempDir, "classes.jar").renameTo(classes));
      PsiTestUtil.addLibrary(myFixture.getModule(), classes.getPath());

      myFixture.setTestDataPath(tempDir.getPath());
      myFixture.copyDirectoryToProject("res", "res");

      myFixture.setTestDataPath(getTestDataPath());
      i++;
    }

    mySettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    AndroidCodeStyleSettingsModifier.modify(mySettings);
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
    myUseCustomSettings = getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS;
    getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = true;
    TestableThumbnailManager.register(myFacet);
    System.setProperty(NavigationSchema.ENABLE_NAV_PROPERTY, "true");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myRootDisposable);
      ThumbnailManager thumbnailManager = ThumbnailManager.getInstance(myFacet);
      if (thumbnailManager instanceof TestableThumbnailManager) {
        ((TestableThumbnailManager)thumbnailManager).deregister();
      }
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
      getAndroidCodeStyleSettings().USE_CUSTOM_SETTINGS = myUseCustomSettings;
      mySettings = null;
      deleteManifest();
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
    return NavModelBuilderUtil.model(name, root, myFacet, myFixture);
  }
}
