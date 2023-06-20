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

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.Collection;
import org.jetbrains.android.AndroidTestCase;

public class LocalResourceRepositoryTest extends AndroidTestCase {
  private static final String TEST_FILE = "xmlpull/layout.xml";

  public void testLocalResourceRepository() throws Exception {
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout1.xml");
    myFixture.copyFileToProject(TEST_FILE, "res/layout/layout2.xml");
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/customfont.ttf");

    LocalResourceRepository resources = StudioResourceRepositoryManager.getModuleResources(myModule);
    assertNotNull(resources);

    Collection<String> layouts = resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());

    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));

    VirtualFile file3 = myFixture.copyFileToProject(TEST_FILE, "res/layout-xlarge-land/layout3.xml");
    waitForUpdates(resources);
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);

    layouts = resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT).keySet();
    assertEquals(3, layouts.size());

    Collection<String> drawables = resources.getResources(ResourceNamespace.TODO(), ResourceType.DRAWABLE).keySet();
    assertEquals(drawables.toString(), 0, drawables.size());
    VirtualFile file4 = myFixture.copyFileToProject(TEST_FILE, "res/drawable-mdpi/foo.png");
    waitForResourceRepositoryUpdates();
    PsiFile psiFile4 = PsiManager.getInstance(getProject()).findFile(file4);
    assertNotNull(psiFile4);
    drawables = resources.getResources(ResourceNamespace.TODO(), ResourceType.DRAWABLE).keySet();
    assertEquals(1, drawables.size());
    assertEquals("foo", drawables.iterator().next());

    WriteCommandAction.runWriteCommandAction(null, psiFile4::delete);
    waitForUpdates(resources);
    drawables = resources.getResources(ResourceNamespace.TODO(), ResourceType.DRAWABLE).keySet();
    assertEquals(0, drawables.size());

    Collection<String> fonts = resources.getResources(ResourceNamespace.TODO(), ResourceType.FONT).keySet();
    assertEquals(1, fonts.size());
    assertEquals("customfont", fonts.iterator().next());

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
