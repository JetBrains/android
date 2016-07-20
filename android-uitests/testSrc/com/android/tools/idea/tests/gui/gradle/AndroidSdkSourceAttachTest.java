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
package com.android.tools.idea.tests.gui.gradle;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.rename;
import static com.intellij.openapi.util.io.FileUtilRt.delete;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class AndroidSdkSourceAttachTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String ANDROID_PLATFORM = "android-23";

  // Sdk used for the simpleApplication project.
  private Sdk mySdk;
  private File mySdkSourcePath;
  private File mySdkSourceTmpPath;

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Before
  public void restoreAndroidSdkSource() throws IOException {
    mySdk = findSuitableAndroidSdk(ANDROID_PLATFORM);
    assumeTrue("SDK with platform '" + ANDROID_PLATFORM + "' not found", mySdk != null);

    String sdkHomePath = mySdk.getHomePath();
    mySdkSourcePath = new File(sdkHomePath, join("sources", ANDROID_PLATFORM));
    mySdkSourceTmpPath = new File(sdkHomePath, join("sources.tmp", ANDROID_PLATFORM)); // it can't be in 'sources' folder

    if (!mySdkSourcePath.isDirectory() && mySdkSourceTmpPath.isDirectory()) {
      rename(mySdkSourceTmpPath, mySdkSourcePath);
    }
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/417 and from IDEA")
  @Test
  public void testDownloadSdkSource() throws IOException {
    if (mySdkSourcePath.isDirectory()) {
      delete(mySdkSourceTmpPath);
      rename(mySdkSourcePath, mySdkSourceTmpPath);
    }
    updateSdkSourceRoot(mySdk);

    guiTest.importSimpleApplication();
    final EditorFixture editor = guiTest.ideFrame().getEditor();

    final VirtualFile classFile = findActivityClassFile();
    editor.open(classFile, EditorFixture.Tab.EDITOR);

    acceptLegalNoticeIfNeeded();

    // Download the source.
    editor.awaitNotification("Sources for '" + mySdk.getName() + "' not found.")
      .performAction("Download");

    DialogFixture downloadDialog = findDialog(withTitle("SDK Quickfix Installation")).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(
      guiTest.robot());
    final JButtonFixture finish = downloadDialog.button(withText("Finish"));

    // Wait until installation is finished. By then the "Finish" button will be enabled.
    Wait.seconds(30).expecting("Android source to be installed").until(finish::isEnabled);
    finish.click();

    Wait.minutes(2).expecting("source file to be opened").until(() -> !classFile.equals(editor.getCurrentFile()));

    VirtualFile sourceFile = editor.getCurrentFile();
    assertNotNull(sourceFile);
    assertIsActivityJavaFile(sourceFile);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/417 and from IDEA")
  @Test
  public void testRefreshSdkSource() throws IOException {
    assumeTrue("Android Sdk Source for '" + mySdk.getName() + "' must be installed before running 'testRefreshSdkSource'",
               mySdkSourcePath.isDirectory());

    SdkModificator sdkModificator = mySdk.getSdkModificator();
    sdkModificator.removeRoots(OrderRootType.SOURCES);
    sdkModificator.commitChanges();

    guiTest.importSimpleApplication();
    final EditorFixture editor = guiTest.ideFrame().getEditor();

    final VirtualFile classFile = findActivityClassFile();
    editor.open(classFile, EditorFixture.Tab.EDITOR);

    acceptLegalNoticeIfNeeded();

    // Refresh the source.
    editor.awaitNotification("Sources for '" + mySdk.getName() + "' not found.")
      .performAction("Refresh (if already downloaded)");

    Wait.minutes(2).expecting("source file to be opened").until(() -> !classFile.equals(editor.getCurrentFile()));

    VirtualFile sourceFile = editor.getCurrentFile();
    assertNotNull(sourceFile);
    assertIsActivityJavaFile(sourceFile);
  }

  private static void assertIsActivityJavaFile(@NotNull VirtualFile sourceFile) {
    assertThat(sourceFile.getPath()).endsWith("android/app/Activity.java");
  }

  private void acceptLegalNoticeIfNeeded() {
    if(!PropertiesComponent.getInstance().isTrueValue("decompiler.legal.notice.accepted")) {
      DialogFixture acceptTermDialog = findDialog(withTitle("JetBrains Decompiler")).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(
        guiTest.robot());
      acceptTermDialog.button(withText("Accept")).click();
    }
  }

  @NotNull
  private VirtualFile findActivityClassFile() {
    VirtualFile jarRoot = null;
    for (VirtualFile file : mySdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
      if (file.getUrl().startsWith(SdkConstants.EXT_JAR)) {
        jarRoot = file;
      }
    }
    assertNotNull(jarRoot);
    VirtualFile classFile = jarRoot.findFileByRelativePath("android/app/Activity.class");
    assertNotNull(classFile);
    return classFile;
  }
}
