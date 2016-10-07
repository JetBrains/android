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
package com.android.tools.idea.actions;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestCase;

public class CreateDeepLinkIntentionActionTest extends AndroidTestCase {
  private static final String BASE_PATH = "deeplink/";
  private static final String ANDROID_MANIFEST = "insideactivity/AndroidManifest.xml";
  private static final String ANDROID_MANIFEST_WITH_DEEP_LINK = "withdeeplink/AndroidManifest.xml";
  private static final String EXPECTED_ANDROID_MANIFEST = "expected/AndroidManifest.xml";
  private static final String EXPECTED_ANDROID_MANIFEST_WITH_TWO_DEEP_LINK =
      "expected/AndroidManifest_withTwoDeepLink.xml";
  private static final String ANDROID_MANIFEST_CARET_OUTSIDE_ACTIVITY =
      "outsideactivity/AndroidManifest.xml";
  private static final String JAVA_FILE = "Class.java";

  public CreateDeepLinkIntentionActionTest() {
    super(false);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
  }

  public void testCreateDeepLinkIntentFilter() throws Exception {
    final XmlFile expectedXmlFile = (XmlFile)myFixture.configureByFile(BASE_PATH + EXPECTED_ANDROID_MANIFEST);
    final XmlFile xmlFile = (XmlFile)myFixture.configureByFile(BASE_PATH + ANDROID_MANIFEST);
    final CreateDeepLinkIntentionAction action = new CreateDeepLinkIntentionAction();
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), xmlFile));
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            action.invoke(myFixture.getProject(), myFixture.getEditor(), xmlFile);
          }
        });
      }
    }, "", "");
    assertEquals(normalizeText(expectedXmlFile.getText()), normalizeText(xmlFile.getText()));
  }

  public void testCreateSecondDeepLinkIntentFilter() throws Exception {
    final XmlFile expectedXmlFile = (XmlFile)myFixture.configureByFile(
        BASE_PATH + EXPECTED_ANDROID_MANIFEST_WITH_TWO_DEEP_LINK);
    final XmlFile xmlFile = (XmlFile)myFixture.configureByFile(
        BASE_PATH + ANDROID_MANIFEST_WITH_DEEP_LINK);
    final CreateDeepLinkIntentionAction action = new CreateDeepLinkIntentionAction();
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), xmlFile));
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            action.invoke(myFixture.getProject(), myFixture.getEditor(), xmlFile);
          }
        });
      }
    }, "", "");
    assertEquals(normalizeText(expectedXmlFile.getText()), normalizeText(xmlFile.getText()));
  }

  public void testCaretOutsideActivity() throws Exception {
    final PsiFile xmlFile = myFixture.configureByFile(
        BASE_PATH + ANDROID_MANIFEST_CARET_OUTSIDE_ACTIVITY);
    final CreateDeepLinkIntentionAction action =
        new CreateDeepLinkIntentionAction();
    assertFalse(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), xmlFile));
  }

  public void testNotAndroidManifestFile() throws Exception {
    final PsiFile xmlFile = myFixture.configureByFile(
      BASE_PATH + JAVA_FILE);
    final CreateDeepLinkIntentionAction action =
      new CreateDeepLinkIntentionAction();
    assertFalse(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), xmlFile));
  }

  public static String normalizeText(String text) {
    return text.replaceAll("\\s+", "");
  }
}
