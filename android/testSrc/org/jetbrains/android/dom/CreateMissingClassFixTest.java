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
package org.jetbrains.android.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.AndroidLintTest;

public class CreateMissingClassFixTest extends AndroidDomTest {
  public CreateMissingClassFixTest() {
    super(false, "dom/manifest");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return null;
  }

  public void testMissingActivityClass() throws Exception {
    final VirtualFile file = copyFileToProject("activity_missing_class.xml", "AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(file);

    final IntentionAction action = AndroidLintTest.getIntentionAction("Create class 'MyActivity'", myFixture);
    assertNotNull(action);

    action.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    final PsiClass psiClass =
      JavaPsiFacade.getInstance(getProject()).findClass("p1.p2.MyActivity", GlobalSearchScope.allScope(getProject()));

    // Class has been created
    assertNotNull(psiClass);
  }

  public void testMissingApplicationClass() throws Exception {
    final VirtualFile file = copyFileToProject("application_missing_class.xml", "AndroidManifest.xml");
    myFixture.configureFromExistingVirtualFile(file);

    final IntentionAction action = AndroidLintTest.getIntentionAction("Create class 'MyApplication'", myFixture);
    assertNotNull(action);

    action.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    final PsiClass psiClass =
      JavaPsiFacade.getInstance(getProject()).findClass("p1.p2.MyApplication", GlobalSearchScope.allScope(getProject()));

    assertNotNull(psiClass);
  }
}
