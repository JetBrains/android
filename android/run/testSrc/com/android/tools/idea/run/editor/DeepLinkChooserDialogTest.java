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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.test.testutils.TestUtils;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeepLinkChooserDialogTest {

  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.onDisk();

  @Rule
  public EdtRule edtRule = new EdtRule();

  @Before
  public void setUp() throws Exception {
    myProjectRule.getFixture().setTestDataPath(TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/deeplink/launch").toString());
    TemplateManagerImpl.setTemplateTesting(myProjectRule.getTestRootDisposable());
  }

  @Test
  @RunsInEdt
  public void testMatchDeeplink() {
    PsiFile file = myProjectRule.getFixture().configureByFile("AndroidManifest.xml");

    XmlFile xmlFile = (XmlFile)file;

    List<String> deepLinks = DeepLinkChooserDialog.getAllDeepLinks(xmlFile.getRootTag());
    assertEquals(3, deepLinks.size());
    assertTrue(deepLinks.contains("http://www.company.com/view1"));
    assertTrue(deepLinks.contains("example://gizmos"));
    assertTrue(deepLinks.contains("deeplink://www.company.com/view6"));
  }
}
