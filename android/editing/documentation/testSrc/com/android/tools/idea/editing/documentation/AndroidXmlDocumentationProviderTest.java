/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editing.documentation;

import static com.google.common.truth.Truth.assertThat;

import com.android.test.testutils.TestUtils;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.navigation.CtrlMouseHandler;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link AndroidXmlDocumentationProvider}.
 */
@RunWith(JUnit4.class)
public class AndroidXmlDocumentationProviderTest {
  private static final String BASE_PATH = "documentation/";

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.withSdk();

  private CodeInsightTestFixture myFixture;

  @Before
  public void setUp() {
    myFixture = projectRule.getFixture();
    myFixture.setTestDataPath(TestUtils.resolveWorkspacePath("tools/adt/idea/android/editing/documentation/testData").toString());
  }

  @Test
  public void valueResourceReferenceQuickDoc() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "valueResourceReferenceQuickDoc.xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);

    ApplicationManager.getApplication().runReadAction(() -> {
      final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertThat(ref).isNotNull();
      assertThat(CtrlMouseHandler.getInfo(ref.resolve(), ref.getElement())).isEqualTo("@string/myString");
    });
  }

  @Test
  public void androidAttributeDocumentation() {
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "androidAttributeDocumentation.xml", "res/layout/test.xml");
    myFixture.configureFromExistingVirtualFile(f);

    ApplicationManager.getApplication().runReadAction(() -> {
      final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertThat(ref).isNotNull();
      // There aren't really mouse-hover quick docs for attributes.
      assertThat(CtrlMouseHandler.getInfo(ref.resolve(), ref.getElement())).isEqualTo("Attribute \"foreground\"");
      // However, one can get the more detailed docs.
      PsiElement docTargetElement = DocumentationManager.getInstance(myFixture.getProject()).findTargetElement(
        myFixture.getEditor(), myFixture.getFile(), ref.getElement());
      DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(docTargetElement);
      assertThat(documentationProvider.generateDoc(docTargetElement, ref.getElement())).isEqualTo(
        "<html><body>Formats: color, reference<br><br>Defines the drawable to draw over the content. This can be used as an overlay.\n" +
        "             The foreground drawable participates in the padding of the content if the gravity\n" +
        "             is set to fill.</body></html>");
    });
  }
}
