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
package com.android.tools.idea.res;

import com.android.resources.ResourceType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;

import java.util.Collection;

public class LocalResourceRepositoryTest extends AndroidTestCase {
  private static final String TEST_FILE = "xmlpull/layout.xml";

  public void test1() {
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout2.xml");

    LocalResourceRepository resources = ModuleResourceRepository.getModuleResources(myModule, true);
    assertNotNull(resources);

    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(2, layouts.size());

    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout2"));

    long generation = resources.getModificationCount();
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout2.xml");
    assertEquals(generation, resources.getModificationCount());

    VirtualFile file3 = myFixture.copyFileToProject(TEST_FILE, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);

    layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(3, layouts.size());

    Collection<String> drawables = resources.getItemsOfType(ResourceType.DRAWABLE);
    assertEquals(drawables.toString(), 0, drawables.size());
    VirtualFile file4 = myFixture.copyFileToProject(TEST_FILE, "res/drawable-mdpi/foo.png");
    final PsiFile psiFile4 = PsiManager.getInstance(getProject()).findFile(file4);
    assertNotNull(psiFile4);
    drawables = resources.getItemsOfType(ResourceType.DRAWABLE);
    assertEquals(1, drawables.size());
    assertEquals("foo", drawables.iterator().next());

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        psiFile4.delete();
      }
    });
    drawables = resources.getItemsOfType(ResourceType.DRAWABLE);
    assertEquals(0, drawables.size());

    // Try deleting a whole resource directory and ensure we're notified of the missing files within
    /* This does not yet work: We don't respond to resource directory removes;
       the PSI listener hook does not get access to the individual files in the directory
       that were removed (even if checking in a before-hook.) To fix this we need to handle the
       way the resources are kept up to date; when we do the PSI rewrite that would be a good time.

    assertEquals(3, layouts.size());
    final PsiDirectory directory = psiFile4.getContainingDirectory();
    assertNotNull(directory);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        directory.delete();
      }
    });
    layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(2, layouts.size());
    */

    /* TODO: The PSI change listener does not appear to fire when run in the unit test; figure out why.
    layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(layouts.size(), 3);

    myFixture.copyFileToProject(TEST_FILE, "res/layout-en/layout2.xml");
    assertEquals(generation + 1, resources.getModificationCount());
    */
  }
}
