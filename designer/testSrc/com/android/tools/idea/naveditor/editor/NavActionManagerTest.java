/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor;

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;

import java.awt.*;
import java.io.File;

import static com.android.tools.idea.naveditor.editor.NavActionManager.Destination;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NavActionManager}
 */
public class NavActionManagerTest extends NavigationTestCase {
  public void testAddElement() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"))).build();

    NavDesignSurface surface = (NavDesignSurface)model.getSurface();

    PsiClass psiClass =
      JavaPsiFacade.getInstance(getProject()).findClass("mytest.navtest.MainActivity", GlobalSearchScope.allScope(getProject()));

    new AddMenuWrapper(surface, ImmutableList.of())
      .addElement(Destination.Companion.create(null, psiClass, "activity", mock(Image.class)), surface);

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<fragment>, instance=2}\n" +
                 "    NlComponent{tag=<activity>, instance=3}", new NlTreeDumper().toTree(model.getComponents()));
  }

  public void testAddElementInSubflow() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                navigationComponent("subflow").
                                  unboundedChildren(fragmentComponent("fragment2")),
                                fragmentComponent("fragment1"))).build();

    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    when(surface.getCurrentNavigation()).thenReturn(model.find("subflow"));

    PsiClass psiClass =
      JavaPsiFacade.getInstance(getProject()).findClass("mytest.navtest.MainActivity", GlobalSearchScope.allScope(getProject()));
    NavActionManager.Companion.addElement(Destination.Companion.create(null, psiClass, "activity", mock(Image.class)), surface);

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<navigation>, instance=1}\n" +
                 "        NlComponent{tag=<fragment>, instance=2}\n" +
                 "        NlComponent{tag=<activity>, instance=3}\n" +
                 "    NlComponent{tag=<fragment>, instance=4}", new NlTreeDumper().toTree(model.getComponents()));
  }

  public void testGetDestinations() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                navigationComponent("subflow").
                                  unboundedChildren(fragmentComponent("fragment2")),
                                fragmentComponent("fragment1"))).build();
    invokeGradleTasks(getProject(), "assembleDebug");
    // TODO: why is this needed?
    myFixture.getTempDirFixture().getFile("../testGetDestinations/app/build/generated/source/r/debug/mytest/navtest/R.java");

    NavDesignSurface surface = (NavDesignSurface)model.getSurface();

    NavActionManager actionManager = new NavActionManager(surface);
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(myFixture.getTempDirPath()));
    assertThat(root).isNotNull();
    VirtualFile virtualFile = root.findFileByRelativePath("../testGetDestinations/app/src/main/res/layout/activity_main2.xml");
    XmlFile xmlFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(virtualFile);

    Destination expected1 =
      new Destination(xmlFile, "MainActivity", "mytest.navtest.MainActivity", "activity", NavActionManagerKt.ACTIVITY_IMAGE);
    Destination expected2 =
      new Destination(null, "BlankFragment", "mytest.navtest.BlankFragment", "fragment", NavActionManagerKt.ACTIVITY_IMAGE);
    assertEquals(ImmutableList.of(expected1, expected2), actionManager.getDestinations());
  }
}
