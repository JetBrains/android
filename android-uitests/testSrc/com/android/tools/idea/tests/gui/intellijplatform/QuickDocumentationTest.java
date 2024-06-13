/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.intellijplatform;

import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.InputEvent.CTRL_MASK;

import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DocumentationWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixtureKt;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.openapi.util.SystemInfo;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class QuickDocumentationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(12, TimeUnit.MINUTES);
  private FormFactor selectMobileTab = FormFactor.MOBILE;
  private IdeFrameFixture ideFrame;

  protected static final String BASIC_VIEW_ACTIVITY_TEMPLATE = "Basic Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 30;

  /**
   * Quick Documentation
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 160028cc-0a76-4403-b3a5-c9bf6da40c89
   * <pre>
   *Test Steps:
   *   1) Create a new project using any template / Open existing project.
   *   1a. Empty Views Activity to validate Java and XML documentation
   *   1b. Empty Activity to validate Kotlin quick documentation
   *   2) Click on any class or method name in the java/kotlin or any view like text view in xml file.
   *   3) Press Ctrl + Q."
   *
   * Verification:
   *   1) Quick documentation pops up irrespective of java/kotlin class or in xml file.
   *
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void quickDocumentationForJavaTest() throws Exception{
    setUpBasicViewActivity(Language.Java);

    EditorFixture editor = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/java/android/com/app/MainActivity.java");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.moveBetween("on", "Create");

    getQuickDocumentation();

    String javaMethodDocumentationContent = DocumentationWindowFixture.getDocumentationContent(guiTest.ideFrame());
    assertThat(javaMethodDocumentationContent.contains("onCreate")).isTrue();
    assertThat(javaMethodDocumentationContent.contains("Overrides:")).isTrue();
    assertThat(javaMethodDocumentationContent.contains("psi_element://androidx.fragment.app.FragmentActivity#onCreate(android.os.Bundle)")).isTrue();

    editor.open("app/src/main/res/layout/activity_main.xml");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    SplitEditorFixture splitEditorFixture = SplitEditorFixtureKt.getSplitEditorFixture(editor);
    splitEditorFixture.setCodeMode();

    editor.moveBetween("AppBar", "Layout");
    getQuickDocumentation();
    String xmlDocumentationContent = DocumentationWindowFixture.getDocumentationContent(guiTest.ideFrame());
    assertThat(xmlDocumentationContent.contains("psi_element://androidx.coordinatorlayout.widget.CoordinatorLayout.AttachedBehavior")).isTrue();
  }

  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void quickDocumentationForKotlinTest() throws Exception{
    setUpBasicViewActivity(Language.Kotlin);

    EditorFixture editor = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/java/android/com/app/MainActivity.kt");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.moveBetween("on", "Create");

    getQuickDocumentation();

    String javaMethodDocumentationContent = DocumentationWindowFixture.getDocumentationContent(guiTest.ideFrame());
    assertThat(javaMethodDocumentationContent.contains("onCreate")).isTrue();
    assertThat(javaMethodDocumentationContent.contains("Overrides:")).isTrue();
    assertThat(javaMethodDocumentationContent.contains("FragmentActivity")).isTrue();
  }

  private void setUpBasicViewActivity(Language codingLanguage) {
    WizardUtils.createNewProject(guiTest, BASIC_VIEW_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, codingLanguage);
    guiTest.robot().waitForIdle();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();

    //Clearing notifications present on the screen.
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
  private void getQuickDocumentation() throws InterruptedException {
    if (SystemInfo.isMac) {
      guiTest.robot().pressAndReleaseKey(KeyEvent.VK_F1); // Quick documentation (Ctrl + Q)
    }
    else {
      guiTest.robot().pressAndReleaseKey(KeyEvent.VK_Q, CTRL_MASK); // Quick documentation (Ctrl + Q)
    }
    TimeUnit.SECONDS.sleep(10); // Wait for search to complete
    guiTest.robot().waitForIdle();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}
