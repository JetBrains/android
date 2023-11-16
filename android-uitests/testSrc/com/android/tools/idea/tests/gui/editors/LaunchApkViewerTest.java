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
package com.android.tools.idea.tests.gui.editors;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.SelectPathFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class LaunchApkViewerTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private static final String APK_NAME = "app-debug.apk";

  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: be75b1ab-005d-43f8-97c2-b84efded54ac
   * <pre>
   *   Verifies APK Viewer gets launched when analyzing apk
   *   Test Steps
   *   1. Import a project
   *   2. Build APK
   *   3. Analyze APK
   *   Verification
   *   1. Ensure APK entries appear for classes.dex, AndroidManifest.xml
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void launchApkViewer() throws Exception {
    List<String> apkEntries = guiTest.importSimpleApplication()
                                     .invokeAndWaitForBuildAction(Wait.seconds(180), "Build", "Build Bundle(s) / APK(s)", "Build APK(s)")
                                     .openFromMenu(SelectPathFixture::find, "Build", "Analyze APK...")
                                     .clickOK()
                                     .getEditor()
                                     .getApkViewer(APK_NAME)
                                     .getApkEntries();
    assertThat(apkEntries).contains("AndroidManifest.xml");
    assertThat(apkEntries).contains("classes.dex");
  }
}
