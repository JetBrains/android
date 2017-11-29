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
package com.android.tools.idea.editors;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidEditorTitleProviderTest extends AndroidTestCase {
  public void test() {
    VirtualFile vFile = myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    PsiFile file = PsiManager.getInstance(getProject()).findFile(vFile);
    assertNotNull(file);

    checkTitle("AndroidManifest.xml", null);
    checkTitle("gen/p1/p2/R.java", null);
    checkTitle("res/wrong/path.xml", null);
    checkTitle("res/layout/file.xml", null);

    UISettings uiSettings = UISettings.getInstance();
    boolean prev = uiSettings.getHideKnownExtensionInTabs();
    try {
      uiSettings.setHideKnownExtensionInTabs(false);
      checkTitle("res/layout-land/file.xml", "land/file.xml");
      checkTitle("res/layout-xlarge/file.xml", "xlarge/file.xml");
      checkTitle("res/values-large-hdpi/strings.xml", "large-hdpi/strings.xml");

      uiSettings.setHideKnownExtensionInTabs(true);
      checkTitle("res/layout-land/file.xml", "land/file");
      checkTitle("res/layout-xlarge/file.xml", "xlarge/file");
      checkTitle("res/values-large-hdpi/strings.xml", "large-hdpi/strings");
    }
    finally {
      // For later chained tests
      uiSettings.setHideKnownExtensionInTabs(prev);
    }
  }

  private void checkTitle(@NotNull String path, @Nullable String expected) {
    AndroidEditorTitleProvider provider = new AndroidEditorTitleProvider();
    VirtualFile file = myFixture.copyFileToProject("R.java", path); // file content does not matter
    Project project = getProject();
    String title = provider.getEditorTabTitle(project, file);
    if (expected == null) {
      assertNull(title);
    }
    else {
      assertEquals(expected, title != null ? FileUtil.toSystemIndependentName(title) : null);
    }
  }
}
