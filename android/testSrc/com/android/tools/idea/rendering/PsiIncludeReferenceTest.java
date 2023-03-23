/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestCase;

@SuppressWarnings("SpellCheckingInspection")
public class PsiIncludeReferenceTest extends AndroidTestCase {
  public void testBasic() {
    VirtualFile file1 = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout/designtime.xml");
    assertNotNull(file1);
    VirtualFile file2 = myFixture.copyFileToProject("xmlpull/visible_child.xml", "res/layout/visible_child.xml");
    assertNotNull(file2);
    VirtualFile file3 = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout-land/designtime.xml");
    assertNotNull(file1);

    PsiIncludeReference reference = new PsiIncludeReference(file1);
    assertEquals("designtime", reference.getFromResourceName());
    assertEquals("@layout/designtime", reference.getFromResourceUrl());
    assertEquals(file1, reference.getFromXmlFile(getProject()).getVirtualFile());
    assertEquals(file1, LocalFileSystem.getInstance().findFileByIoFile(reference.getFromPath()));
    //noinspection ConstantConditions

    reference = new PsiIncludeReference(file3);
    assertEquals("designtime", reference.getFromResourceName());
    assertEquals("@layout/designtime", reference.getFromResourceUrl());
  }

  public void testGetSet() {
    VirtualFile included = myFixture.copyFileToProject("designer/included.xml", "res/layout/included.xml");
    assertNotNull(included);
    VirtualFile includer = myFixture.copyFileToProject("designer/included.xml", "res/layout/includer.xml");
    assertNotNull(includer);
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(included);
    assertNotNull(psiFile);

    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = manager.getConfiguration(included);
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    assertNotNull(resourceResolver);
    PsiIncludeReference reference = (PsiIncludeReference)PsiIncludeReference.get(psiFile, resourceResolver);
    assertEquals("includer", reference.getFromResourceName());
    assertEquals("@layout/includer", reference.getFromResourceUrl());

    assertEquals(reference.getFromXmlFile(getProject()).getVirtualFile(), includer);

    IncludingLayout.setIncludingLayout(psiFile, null);
    assertEquals(PsiIncludeReference.NONE, PsiIncludeReference.get(psiFile, resourceResolver));

    VirtualFile other = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout/designtime.xml");
    assertNotNull(other);
    IncludingLayout.setIncludingLayout(psiFile, "@layout/designtime");
    manager.getResolverCache().reset();
    assertEquals("@layout/designtime", PsiIncludeReference.get(psiFile, configuration.getResourceResolver()).getFromResourceUrl());
  }
}