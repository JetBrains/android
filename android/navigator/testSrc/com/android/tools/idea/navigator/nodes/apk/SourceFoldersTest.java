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
package com.android.tools.idea.navigator.nodes.apk;

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.testing.ProjectFiles.createFolder;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Tests for {@link SourceFolders}.
 */
public class SourceFoldersTest extends HeavyPlatformTestCase {
  private Module myAppModule;
  private VirtualFile mySrcFolder;
  private VirtualFile myMypackageFolder;
  private VirtualFile myLibFolder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    VirtualFile appFolder = createFolderInProjectRoot(getProject(), "app");
    myAppModule = createModuleAt("app", getProject(), getModuleType(), appFolder.toNioPath());
    mySrcFolder = createFolder(appFolder, "src");
    myMypackageFolder = createFolder(mySrcFolder, "mypackage");
    myLibFolder = createFolder(appFolder, "lib");
  }

  public void testIsInSourceFolderForProject() {
    Project project = getProject();
    assertFalse(SourceFolders.isInSourceFolder(mySrcFolder, project));

    // Make "src" a source folder.
    ModifiableRootModel moduleModel = ModuleRootManager.getInstance(myAppModule).getModifiableModel();
    moduleModel.addContentEntry(mySrcFolder);
    ApplicationManager.getApplication().runWriteAction(moduleModel::commit);

    assertTrue(SourceFolders.isInSourceFolder(mySrcFolder, project));
    assertTrue(SourceFolders.isInSourceFolder(myMypackageFolder, project));
    assertFalse(SourceFolders.isInSourceFolder(myLibFolder, project));
  }

  public void testIsInSourceFolderForLibrary() {
    NativeLibrary library = new NativeLibrary("test") {
      @Override
      @NotNull
      public List<String> getSourceFolderPaths() {
        List<String> paths = new ArrayList<>();
        paths.add(virtualToIoFile(mySrcFolder).getPath());
        return paths;
      }
    };

    assertTrue(SourceFolders.isInSourceFolder(mySrcFolder, library));
    assertTrue(SourceFolders.isInSourceFolder(myMypackageFolder, library));
    assertFalse(SourceFolders.isInSourceFolder(myLibFolder, library));
  }
}