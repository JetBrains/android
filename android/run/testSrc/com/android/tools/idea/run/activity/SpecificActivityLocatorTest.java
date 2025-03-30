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

import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ALIAS;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_UNDECLARED;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;

public class SpecificActivityLocatorTest extends AndroidTestCase {

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testValidLauncherActivity() throws ActivityLocator.ActivityLocatorException {
    VirtualFile manifestFile = myFixture.copyFileToProject("projects/runConfig/activity/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    VirtualFile launcherFile = myFixture.copyFileToProject("projects/runConfig/activity/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(launcherFile)).isNotNull();

    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher");
    locator.validate();
  }

  public void testActivityNotDeclared() {
    VirtualFile manifestFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    VirtualFile launcherFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/Launcher2.java", "src/com/example/unittest/Launcher2.java");
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(launcherFile)).isNotNull();

    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher2");
    try {
      locator.validate();
      fail("Validation succeeded even without activity declaration.");
    }
    catch (ActivityLocator.ActivityLocatorException e) {
      assertEquals("The activity 'Launcher2' is not declared in AndroidManifest.xml", e.getMessage());
    }
  }

  public void testNonActivity() {
    VirtualFile manifestFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher");
    try {
      locator.validate();
      fail("Invalid activity accepted");
    } catch (ActivityLocator.ActivityLocatorException e) {
      assertEquals("com.example.unittest.Launcher is not an Activity subclass or alias", e.getMessage());
    }
  }

  public void testValidLauncherAlias() throws ActivityLocator.ActivityLocatorException {
    VirtualFile manifestFile = myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    VirtualFile launcherFile = myFixture.copyFileToProject(RUN_CONFIG_ALIAS + "/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(launcherFile)).isNotNull();

    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.LauncherAlias");
    locator.validate();
  }

  public void testAliasNotDeclared() {
    VirtualFile manifestFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    VirtualFile launcherFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/Launcher.java", "src/com/example/unittest/Launcher.java");
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(launcherFile)).isNotNull();


    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.NotLaunchable");
    try {
      locator.validate();
      fail("Validation succeeded for activity alias that isn't launchable.");
    } catch (ActivityLocator.ActivityLocatorException e) {
      assertEquals("The activity must be exported or contain an intent-filter", e.getMessage());
    }
  }

  public void testActivityWithoutLauncherIntent() {
    VirtualFile manifestFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    VirtualFile launcherFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/Launcher.java", "src/com/example/unittest/Launcher.java");
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(launcherFile)).isNotNull();


    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher");
    try {
      locator.validate();
      fail("Validation succeeded for activity that isn't launchable.");
    } catch (ActivityLocator.ActivityLocatorException e) {
      assertEquals("The activity must be exported or contain an intent-filter", e.getMessage());
    }
  }

  public void testActivityWithSomeLauncherIntent() throws ActivityLocator.ActivityLocatorException {
    VirtualFile manifestFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    VirtualFile launcherFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/Launcher.java", "src/com/example/unittest/Launcher.java");
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(launcherFile)).isNotNull();

    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.SendHandler");
    locator.validate();
  }

  public void testExportedActivity() throws ActivityLocator.ActivityLocatorException {
    VirtualFile manifestFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(manifestFile)).isNotNull();

    VirtualFile launcherFile = myFixture.copyFileToProject(RUN_CONFIG_UNDECLARED + "/ExportedActivity.java", "src/com/example/unittest/ExportedActivity.java");
    assertThat(PsiManager.getInstance(myFixture.getProject()).findFile(launcherFile)).isNotNull();

    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.ExportedActivity");
    locator.validate();
  }
}
