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
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static com.android.tools.idea.tests.gui.framework.GuiTests.skip;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.rename;
import static com.intellij.openapi.util.io.FileUtilRt.delete;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.swing.timing.Pause.pause;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;
import static org.junit.Assert.assertNotNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class AndroidSdkSourceAttachTest extends GuiTestCase {
  private static final String ANDROID_PLATFORM = "android-23";

  // Sdk used for the simpleApplication project.
  private Sdk mySdk;
  private File mySdkSourcePath;
  private File mySdkSourceTmpPath;

  @Before
  public void restoreAndroidSdkSource() throws IOException {
    mySdk = findSuitableAndroidSdk(ANDROID_PLATFORM);

    if (mySdk != null) {
      String sdkHomePath = mySdk.getHomePath();
      mySdkSourcePath = new File(sdkHomePath, join("sources", ANDROID_PLATFORM));
      mySdkSourceTmpPath = new File(sdkHomePath, join("sources.tmp", ANDROID_PLATFORM)); // it can't be in 'sources' folder

      if (!mySdkSourcePath.isDirectory() && mySdkSourceTmpPath.isDirectory()) {
        rename(mySdkSourceTmpPath, mySdkSourcePath);
      }
    }
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/326 and from IDEA")
  @Test @IdeGuiTest
  public void testDownloadSdkSource() throws IOException {
    if (mySdk == null) {
      printPlatformNotFound();
      skip("testDownloadSdkSource");
      return;
    }

    if (mySdkSourcePath.isDirectory()) {
      delete(mySdkSourceTmpPath);
      rename(mySdkSourcePath, mySdkSourceTmpPath);
    }
    updateSdkSourceRoot(mySdk);

    myProjectFrame = importSimpleApplication();
    final EditorFixture editor = myProjectFrame.getEditor();

    final VirtualFile classFile = findActivityClassFile();
    editor.open(classFile, EditorFixture.Tab.EDITOR);

    acceptLegalNoticeIfNeeded();

    // Download the source.
    findNotificationPanel().performAction("Download");

    DialogFixture downloadDialog = findDialog(withTitle("SDK Quickfix Installation")).withTimeout(SHORT_TIMEOUT.duration()).using(myRobot);
    final JButtonFixture finish = downloadDialog.button(withText("Finish"));

    // Wait until installation is finished. By then the "Finish" button will be enabled.
    pause(new Condition("Android source is installed") {
      @Override
      public boolean test() {
        return finish.isEnabled();
      }
    });
    finish.click();

    pause(new Condition("Source file is opened") {
      @Override
      public boolean test() {
        return !classFile.equals(editor.getCurrentFile());
      }
    }, SHORT_TIMEOUT);

    VirtualFile sourceFile = editor.getCurrentFile();
    assertNotNull(sourceFile);
    assertIsActivityJavaFile(sourceFile);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/326 and from IDEA")
  @Test @IdeGuiTest
  public void testRefreshSdkSource() throws IOException {
    if (mySdk == null) {
      printPlatformNotFound();
      skip("testRefreshSdkSource");
      return;
    }

    if (!mySdkSourcePath.isDirectory()) {
      // Skip test if Sdk source is not installed.
      System.out.println("Android Sdk Source for '" + mySdk.getName() + "' must be installed before running 'testRefreshSdkSource'");
      skip("testRefreshSdkSource");
      return;
    }

    SdkModificator sdkModificator = mySdk.getSdkModificator();
    sdkModificator.removeRoots(OrderRootType.SOURCES);
    sdkModificator.commitChanges();

    myProjectFrame = importSimpleApplication();
    final EditorFixture editor = myProjectFrame.getEditor();

    final VirtualFile classFile = findActivityClassFile();
    editor.open(classFile, EditorFixture.Tab.EDITOR);

    acceptLegalNoticeIfNeeded();

    // Refresh the source.
    findNotificationPanel().performAction("Refresh (if already downloaded)");

    pause(new Condition("Source file is opened") {
      @Override
      public boolean test() {
        return !classFile.equals(editor.getCurrentFile());
      }
    }, SHORT_TIMEOUT);

    VirtualFile sourceFile = editor.getCurrentFile();
    assertNotNull(sourceFile);
    assertIsActivityJavaFile(sourceFile);
  }

  private static void printPlatformNotFound() {
    System.out.println("SDK with platform '" + ANDROID_PLATFORM + "' not found");
  }

  private static void assertIsActivityJavaFile(@NotNull VirtualFile sourceFile) {
    assertThat(sourceFile.getPath()).endsWith("android/app/Activity.java");
  }

  private void acceptLegalNoticeIfNeeded() {
    if(!PropertiesComponent.getInstance().isTrueValue("decompiler.legal.notice.accepted")) {
      DialogFixture acceptTermDialog = findDialog(withTitle("JetBrains Decompiler")).withTimeout(SHORT_TIMEOUT.duration()).using(myRobot);
      acceptTermDialog.button(withText("Accept")).click();
    }
  }

  @NotNull
  private EditorNotificationPanelFixture findNotificationPanel() {
    return myProjectFrame.requireEditorNotification(
      "Sources for '" + mySdk.getName() + "' not found.");
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
