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
package com.android.tools.idea.apk.debugging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;

import java.io.IOException;

import static com.android.tools.idea.testing.ProjectFiles.createFileInProjectRoot;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;

/**
 * Tests for {@link LibraryFolder}.
 */
public class LibraryFolderTest extends IdeaTestCase {
  public void testGetName() {
    assertEquals("lib", LibraryFolder.getName());
  }

  public void testFindInProjectWithLibFolderPresent() throws IOException {
    Project project = getProject();
    VirtualFile libFolder = createFolderInProjectRoot(project, "lib");
    VirtualFile found = LibraryFolder.findIn(project);
    assertEquals(libFolder, found);
  }

  public void testFindInProjectWithLibFilePresent() throws IOException {
    Project project = getProject();
    createFileInProjectRoot(project, "lib");
    VirtualFile found = LibraryFolder.findIn(project);
    assertNull(found);
  }

  public void testFindInProjectWithoutLibFolder() {
    VirtualFile found = LibraryFolder.findIn(getProject());
    assertNull(found);
  }
}