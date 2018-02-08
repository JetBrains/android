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
package com.android.tools.idea.naveditor.dom;

import com.android.tools.idea.naveditor.NavTestCase;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.impl.DomManagerImpl;

public class NavigationSchemaTest3 extends NavTestCase {
  public void testSchemaNotInitialized() {
    PsiManager psiManager = PsiManager.getInstance(getProject());
    XmlFile psiFile = (XmlFile)psiManager.findFile(myFixture.findFileInTempDir("res/navigation/navigation.xml"));

    DomManagerImpl domManager = DomManagerImpl.getDomManager(getProject());
    XmlTag tag = psiFile.getRootTag().getSubTags()[0];
    assertNotNull(domManager.getDomHandler(tag));
  }
}
