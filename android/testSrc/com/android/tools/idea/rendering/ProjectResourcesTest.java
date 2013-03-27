/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;

import java.util.Collection;

public class ProjectResourcesTest extends AndroidTestCase {
  private static final String TEST_FILE = "xmlpull/layout.xml";

  public void test1() {
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout2.xml");

    ProjectResources resources = ProjectResources.get(myModule);
    assertNotNull(resources);

    Collection<ResourceItem> layouts = resources.getResourceItemsOfType(ResourceType.LAYOUT);
    assertEquals(layouts.size(), 2);

    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout2"));

    int generation = resources.getGeneration();
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout2.xml");
    assertEquals(generation, resources.getGeneration());

    VirtualFile file3 = myFixture.copyFileToProject(TEST_FILE, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);

    /* TODO: The PSI change listener does not appear to fire when run in the unit test; figure out why.
    layouts = resources.getResourceItemsOfType(ResourceType.LAYOUT);
    assertEquals(layouts.size(), 3);

    myFixture.copyFileToProject(TEST_FILE, "res/layout-en/layout2.xml");
    assertEquals(generation + 1, resources.getGeneration());
    */
  }
}
