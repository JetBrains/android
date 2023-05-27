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

import com.android.tools.idea.testing.AndroidTestUtils;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.inspections.XmlWrongFileTypeInspection;

public class XmlWrongFileTypeInspectionTest extends AndroidDomTestCase {
  public XmlWrongFileTypeInspectionTest() {
    super("dom/anim");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    // Avoid magical functions to be used
    throw new IllegalStateException();
  }

  public void testInvokeInspection() throws Exception {
    final VirtualFile virtualFile = copyFileToProject("animatedVector.xml", "res/anim/animatedVector.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.enableInspections(XmlWrongFileTypeInspection.class);

    final IntentionAction action = AndroidTestUtils.getIntentionAction(myFixture, "Move file to \"drawable\"");
    assertNotNull(action);

    WriteAction.run(() -> action.invoke(getProject(), myFixture.getEditor(), myFixture.getFile()));

    assertNull(LocalFileSystem.getInstance().refreshAndFindFileByPath("res/anim/animatedVector.xml"));
    assertTrue(virtualFile.getPath().endsWith("res/drawable/animatedVector.xml"));
  }
}
