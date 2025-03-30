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
package com.android.tools.idea.testing;

import static com.intellij.openapi.util.io.FileUtil.createIfDoesntExist;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.junit.Assert.assertNotNull;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import java.io.IOException;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

public final class ProjectFiles {
  private ProjectFiles() {
  }

  @NotNull
  public static VirtualFile createFolderInProjectRoot(@NotNull Project project, @NotNull String folderName) throws IOException {
    return createFolder(PlatformTestUtil.getOrCreateProjectBaseDir(project), folderName);
  }

  @NotNull
  public static VirtualFile createFolder(@NotNull VirtualFile parent, @NotNull String folderName) throws IOException {
    VirtualFile folder = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return parent.createChildDirectory(this, folderName);
      }
    });
    assertNotNull(folder);
    return folder;
  }

  @NotNull
  public static VirtualFile createFileInProjectRoot(@NotNull Project project, @NotNull String fileName) throws IOException {
    VirtualFile parent = PlatformTestUtil.getOrCreateProjectBaseDir(project);
    return createFile(parent, fileName);
  }

  @NotNull
  public static VirtualFile createFile(@NotNull VirtualFile parent, @NotNull String fileName) throws IOException {
    VirtualFile file = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return parent.createChildData(this, fileName);
      }
    });
    assertNotNull(file);
    return file;
  }

  @NotNull
  public static Module createModule(@NotNull Project project, @NotNull String name) {
    @SystemIndependent String projectRootFolder = project.getBasePath();
    File moduleFile = new File(toSystemDependentName(projectRootFolder), name + ModuleFileType.DOT_DEFAULT_EXTENSION);
    createIfDoesntExist(moduleFile);

    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
    return createModule(project, virtualFile, EmptyModuleType.getInstance());
  }

  @NotNull
  public static Module createModule(@NotNull Project project, @NotNull File modulePath, @NotNull ModuleType<?> type) {
    VirtualFile moduleFolder = findFileByIoFile(modulePath, true);
    TestCase.assertNotNull(moduleFolder);
    return createModule(project, moduleFolder, type);
  }

  @NotNull
  private static Module createModule(@NotNull Project project, @NotNull VirtualFile file, @NotNull ModuleType<?> type) {
    return WriteAction.compute(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      Module module = moduleManager.newModule(file.getPath(), type.getId());
      module.getModuleFile();
      return module;
    });
  }
}
