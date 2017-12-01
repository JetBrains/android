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

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.naveditor.NavGradleTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;

import java.io.File;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link NavActionManager} that require a complete gradle project to run.
 */
public class NavActionManagerGradleTest extends NavGradleTestCase {

  public void testGetDestinations() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
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

    NavActionManager.Destination expected1 =
      new NavActionManager.Destination(xmlFile, "MainActivity", "mytest.navtest.MainActivity", "activity", NavActionManagerKt.ACTIVITY_IMAGE);
    NavActionManager.Destination expected2 =
      new NavActionManager.Destination(null, "BlankFragment", "mytest.navtest.BlankFragment", "fragment", NavActionManagerKt.ACTIVITY_IMAGE);
    assertEquals(ImmutableList.of(expected1, expected2), actionManager.getDestinations());
  }
}
