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
package com.android.tools.idea.run.editor;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestCase;

import java.util.List;

public class DeepLinkChooserDialogTest extends AndroidTestCase {
  private static final String BASE_PATH = "deeplink/launch/";
  private static final String ANDROID_MANIFEST = "AndroidManifest.xml";

  public DeepLinkChooserDialogTest() {
    super(false);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
  }

  public void testMatchDeeplink() throws Exception {
    PsiFile file = myFixture.configureByFile(BASE_PATH + ANDROID_MANIFEST);

    XmlFile xmlFile = (XmlFile) file;

    List<String> deepLinks = DeepLinkChooserDialog.getAllDeepLinks(xmlFile.getRootTag());
    assertEquals(3, deepLinks.size());
    assertTrue(deepLinks.contains("http://www.company.com/view1"));
    assertTrue(deepLinks.contains("example://gizmos"));
    assertTrue(deepLinks.contains("deeplink://www.company.com/view6"));
  }
}
