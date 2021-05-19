/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import static com.android.tools.idea.concurrency.AsyncTestUtils.waitForCondition;
import static com.android.tools.idea.model.AndroidManifestIndexQueryUtils.queryActivitiesFromManifestIndex;
import static com.android.tools.idea.run.activity.DefaultActivityLocator.getActivitiesFromMergedManifest;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ACTIVITY;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ALIAS;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_DEFAULT;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ENABLED;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_MANIFESTS;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_TV;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.flags.StudioFlags.DefaultActivityLocatorStrategy;
import com.android.tools.idea.model.ActivitiesAndAliases;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestModificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link DefaultActivityLocator}.
 */
public class DefaultActivityLocatorTest extends AndroidTestCase {

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.clearOverride();
    } finally {
      super.tearDown();
    }
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  private void renameClass(@NotNull String oldName, @NotNull String newName) {
    PsiClass classToRename = myFixture.findClass(oldName);
    myFixture.renameElement(classToRename, newName);
  }

  private void runAndWaitForMergedManifestUpdate(@NotNull Runnable runnable) throws Exception {
    ModificationTracker modificationTracker = MergedManifestManager.getModificationTracker(myModule);
    long modificationCount = modificationTracker.getModificationCount();
    runnable.run();
    waitForCondition(2, TimeUnit.SECONDS, () -> modificationTracker.getModificationCount() > modificationCount);
  }

  @Nullable
  private static String computeDefaultActivity(@NotNull AndroidFacet facet, @Nullable IDevice device) {
    List<DefaultActivityLocator.ActivityWrapper> activities = getActivitiesFromMergedManifest(facet);
    if (device == null) {
      return DefaultActivityLocator.computeDefaultActivity(activities);
    }
    return DefaultActivityLocator.computeDefaultActivityWithDevicePreference(activities, device);
  }

  private static <T> T computeInBackgroundThread(Callable<T> callable) throws Exception {
    return ApplicationManager.getApplication().executeOnPooledThread(callable).get(2, TimeUnit.SECONDS);
  }

  public void testActivity() {
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.Launcher", computeDefaultActivity(myFacet, null));
  }

  public void testActivityAlias() {
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.LauncherAlias", computeDefaultActivity(myFacet, null));
  }

  // tests that when there are multiple activities that with action MAIN and category LAUNCHER, then give
  // preference to the one that also has category DEFAULT
  public void testPreferDefaultCategoryActivity() {
    myFixture.copyFileToProject(RUN_CONFIG_DEFAULT + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.LauncherAlias", computeDefaultActivity(myFacet, null));
  }

  // tests that when there are multiple launcher activities, then we pick the leanback launcher for a TV device
  public void testLeanbackLauncher() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/DefaultLauncher.java",
                                "src/com/example/unittest/DefaultLauncher.java");
    myFixture.copyFileToProject(RUN_CONFIG_TV + "/TvLauncher.java",
                                "src/com/example/unittest/TvLauncher.java");

    IDevice tv = mock(IDevice.class);
    when(tv.supportsFeature(IDevice.HardwareFeature.TV)).thenReturn(true);
    assertEquals("com.example.unittest.TvLauncher", computeDefaultActivity(myFacet, tv));

    IDevice device = mock(IDevice.class);
    when(tv.supportsFeature(IDevice.HardwareFeature.TV)).thenReturn(false);
    assertEquals("com.example.unittest.DefaultLauncher", computeDefaultActivity(myFacet, device));
  }

  // tests that when there are multiple launcher activities, we exclude the ones with android:enabled="false"
  public void testEnabledActivities() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_ENABLED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertEquals("com.example.unittest.LaunchActivity", computeDefaultActivity(myFacet, null));

    // make sure that the dom based approach to getting values works as well
    final Manifest manifest = Manifest.getMainManifest(myFacet);
    assertEquals("com.example.unittest.LaunchActivity", DefaultActivityLocator.getDefaultLauncherActivityName(myFacet.getModule().getProject(), manifest));
  }

  public void testLauncherActivityIntent() throws Exception {
    myFixture.copyFileToProject(RUN_CONFIG_MANIFESTS + "/InvalidCategory.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertNull("No launchable activity registration is present in the manifest, but one was detected",
               computeDefaultActivity(myFacet, null));
  }

  public void testBlockStrategy_blocksOnFreshMergedManifest() {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.BLOCK);

    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");

    assertEquals("com.example.unittest.Launcher", computeDefaultActivity(myFacet, null));
    renameClass("com.example.unittest.Launcher", "NewLauncher");
    assertEquals("com.example.unittest.NewLauncher", computeDefaultActivity(myFacet, null));
  }

  public void testStaleStrategy_blocksBackgroundThreadOnFreshMergedManifest() throws Exception {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.STALE);

    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");

    assertEquals("com.example.unittest.Launcher", computeInBackgroundThread(() -> computeDefaultActivity(myFacet, null)));
    renameClass("com.example.unittest.Launcher", "NewLauncher");
    assertEquals("com.example.unittest.NewLauncher", computeInBackgroundThread(() -> computeDefaultActivity(myFacet, null)));
  }

  public void testStaleStrategy_usesStaleManifestOnEdt() throws Exception {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.STALE);
    ApplicationManager.getApplication().assertIsDispatchThread();

    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");

    // We've never computed the merged manifest, so DefaultActivityLocator won't have a stale manifest to get activities from.
    runAndWaitForMergedManifestUpdate(() -> assertNull(computeDefaultActivity(myFacet, null)));
    // But we should have triggered a merged manifest recompute in a background thread to ensure the manifest is eventually
    // available in future calls.
    assertEquals("com.example.unittest.Launcher", computeDefaultActivity(myFacet, null));

    renameClass("com.example.unittest.Launcher", "NewLauncher");

    // Forced to use a stale view of the merged manifest, DefaultActivityLauncher picks an activity that no longer exists.
    runAndWaitForMergedManifestUpdate(
      () -> assertEquals("com.example.unittest.Launcher", computeDefaultActivity(myFacet, null)));
    // But it eventually is able to identify the correct class once the merged manifest has been recomputed.
    assertEquals("com.example.unittest.NewLauncher", computeDefaultActivity(myFacet, null));
  }

  public void testIndexStrategy_onBackgroundThread() throws Exception {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.INDEX);
    MergedManifestModificationListener.ensureSubscribed(getProject());

    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");

    assertEquals("com.example.unittest.Launcher", computeInBackgroundThread(() -> computeDefaultActivity(myFacet, null)));
    renameClass("com.example.unittest.Launcher", "NewLauncher");
    assertEquals("com.example.unittest.NewLauncher", computeInBackgroundThread(() -> computeDefaultActivity(myFacet, null)));
  }

  public void testIndexStrategy_onEdt() {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.INDEX);
    MergedManifestModificationListener.ensureSubscribed(getProject());
    ApplicationManager.getApplication().assertIsDispatchThread();

    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");

    assertEquals("com.example.unittest.Launcher", computeDefaultActivity(myFacet, null));
    renameClass("com.example.unittest.Launcher", "NewLauncher");
    assertEquals("com.example.unittest.NewLauncher", computeDefaultActivity(myFacet, null));
  }

  public void testIndexStrategy_cacheHit() {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.INDEX);
    MergedManifestModificationListener.ensureSubscribed(getProject());

    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");

    ActivitiesAndAliases activities = queryActivitiesFromManifestIndex(myFacet);
    assertSame(activities, queryActivitiesFromManifestIndex(myFacet));
  }

  public void testIndexStrategy_valid() {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.INDEX);
    MergedManifestModificationListener.ensureSubscribed(getProject());

    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(RUN_CONFIG_ACTIVITY + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");

    DefaultActivityLocator defaultActivityLocator = new DefaultActivityLocator(myFacet);
    try {
      defaultActivityLocator.validate();
    } catch (ActivityLocator.ActivityLocatorException e) {
      fail("A launchable activity registration is present in the manifest, but none was detected.");
    }
  }

  public void testIndexStrategy_invalid() {
    StudioFlags.DEFAULT_ACTIVITY_LOCATOR_STRATEGY.override(DefaultActivityLocatorStrategy.INDEX);
    myFixture.copyFileToProject(RUN_CONFIG_MANIFESTS + "/InvalidCategory.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    DefaultActivityLocator defaultActivityLocator = new DefaultActivityLocator(myFacet);
    try {
      defaultActivityLocator.validate();
    } catch (ActivityLocator.ActivityLocatorException e) {
      return;
    }
    fail("No launchable activity registration is present in the manifest, but one was detected");
  }
}
