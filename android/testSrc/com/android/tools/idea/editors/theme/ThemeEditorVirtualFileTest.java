/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.io.IOException;

public class ThemeEditorVirtualFileTest extends AndroidTestCase {
  /**
   * Tests that the theme editor works with the right virtual file
   * when there are several projects with the same name open.
   */
  public void testRightProject() throws IOException {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project otherProject = projectManager.newProject(getProject().getName(), FileUtilRt.generateRandomTemporaryPath().getPath(), true, true);
    try {
      assertNotNull(otherProject);
      projectManager.openProject(otherProject);

      ThemeEditorVirtualFile themeEditorVirtualFile = ThemeEditorVirtualFile.getThemeEditorFile(myModule.getProject());
      VirtualFile virtualFile = themeEditorVirtualFile.getFileSystem().findFileByPath(themeEditorVirtualFile.getPath());

      assertEquals(themeEditorVirtualFile, virtualFile);
    }
    finally {
      if (otherProject != null) {
        ((ProjectManagerImpl)projectManager).forceCloseProject(otherProject, true);
      }
    }
  }
}