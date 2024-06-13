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
import static java.awt.event.ActionEvent.ALT_MASK;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.SHIFT_MASK;

import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.DefinitionWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixtureKt;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class QuickDefinitionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  private FormFactor selectMobileTab = FormFactor.MOBILE;
  private IdeFrameFixture ideFrame;

  protected static final String BASIC_VIEW_ACTIVITY_TEMPLATE = "Basic Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 30;

  /**
   * Quick Definition
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 128c8103-3773-4865-956f-8b1c90c31be4
   * <pre>
   *Test Steps:
   *   1) Create a new project using any template / Open existing project.
   *   1a. Basic Views Activity to validate Java
   *   1b. Basic Views Activity to validate XML documentation
   *   1c. Basis Views Activity to validate Kotlin quick documentation
   *   2) Click on any class or method name in the java/kotlin or any view like text view in xml file.
   *   3) Press Ctrl + Shift + I."
   *
   * Verification:
   *   1) Quick definition pops up irrespective of java/kotlin class or in xml file.
   *
   * </pre>
   */
  @Test
  public void quickDefinitionForJavaTest() throws Exception{
    setUpBasicViewActivity(Language.Java);

    EditorFixture editor = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/java/android/com/app/MainActivity.java");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.moveBetween("setSupport", "ActionBar(binding.toolbar)")
      .pressAndReleaseKey(quickDefinitionKeyPressInfo());
    waitForQuickDefinitionDialog();
    String javaMethodDocumentationContent = DefinitionWindowFixture.getDefinitionContent(guiTest.ideFrame());
    assertThat(javaMethodDocumentationContent.contains("public void setSupportActionBar(")).isTrue();
  }

  @Test
  public void quickDefinitionForXMLTest() throws Exception {
    setUpBasicViewActivity(Language.Java);

    EditorFixture editor= guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/res/layout/fragment_first.xml");
    SplitEditorFixture splitEditorFixture = SplitEditorFixtureKt.getSplitEditorFixture(editor);
    splitEditorFixture.setSplitMode();

    editor.moveBetween("<Text", "View")
      .pressAndReleaseKey(quickDefinitionKeyPressInfo());
    waitForQuickDefinitionDialog();
    String xmlDocumentationContent = DefinitionWindowFixture.getDefinitionContent(guiTest.ideFrame());
    assertThat(xmlDocumentationContent.contains("public class TextView extends")).isTrue();
  }

  @Test
  public void quickDefinitionForKotlinTest() throws Exception{
    setUpBasicViewActivity(Language.Kotlin);

    EditorFixture editor = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/java/android/com/app/MainActivity.kt");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.moveBetween("setSupport", "ActionBar(binding.toolbar)")
      .pressAndReleaseKey(quickDefinitionKeyPressInfo());
    waitForQuickDefinitionDialog();
    String javaMethodDocumentationContent = DefinitionWindowFixture.getDefinitionContent(guiTest.ideFrame());
    assertThat(javaMethodDocumentationContent.contains("public void setSupportActionBar(")).isTrue();
  }

  private void setUpBasicViewActivity(Language codingLanguage) {
    WizardUtils.createNewProject(guiTest, BASIC_VIEW_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, codingLanguage);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();

    //Clearing notifications present on the screen.
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  private void waitForQuickDefinitionDialog(){
    GuiTests.waitUntilShowing(guiTest.robot(), Matchers.byType(ImplementationViewComponent.class));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  private KeyPressInfo quickDefinitionKeyPressInfo(){
    if (SystemInfo.isMac) {
      return KeyPressInfo.keyCode(KeyEvent.VK_SPACE).modifiers(ALT_MASK); //(Option + Space)
    }
    else {
      return (KeyPressInfo.keyCode(KeyEvent.VK_I)).modifiers(CTRL_MASK,SHIFT_MASK); //(Ctrl + Shift + I)
    }
  }
}
