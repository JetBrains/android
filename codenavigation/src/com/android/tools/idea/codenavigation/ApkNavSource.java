/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.codenavigation;

import com.android.tools.idea.apk.ApkFacet;
import com.google.common.base.Strings;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link NavSource} that searches an APK to convert a {@link CodeLocation} to a
 * {@link Navigatable}.
 */
public class ApkNavSource implements NavSource {
  @NotNull
  private final Project myProject;
  @NotNull
  private final List<LibraryMapping> myLibraryMappings;

  public ApkNavSource(@NotNull Project project) {
    myProject = project;
    myLibraryMappings = getLibraryMappings(project);
  }

  @Nullable
  @Override
  public Navigatable lookUp(@NotNull CodeLocation location, @Nullable String arch) {
    if (!Strings.isNullOrEmpty(location.getFileName()) &&
        location.getLineNumber() != CodeLocation.INVALID_LINE_NUMBER) {
      Navigatable navigatable = getExplicitLocationNavigable(location);
      if (navigatable != null) {
        return navigatable;
      }

      navigatable = getApkMappingNavigable(location);
      if (navigatable != null) {
        return navigatable;
      }
    }

    return null;
  }

  /**
   * Returns a navigation to a file and a line explicitly specified in the location
   * if it exists.
   */
  @Nullable
  private Navigatable getExplicitLocationNavigable(@NotNull CodeLocation location) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile sourceFile = fileSystem.findFileByPath(location.getFileName());
    if (sourceFile == null || !sourceFile.exists()) {
      return null;
    }
    return new OpenFileDescriptor(myProject, sourceFile, location.getLineNumber(), 0);
  }

  /**
   * Returns a navigation to a file and a line explicitly specified in the location
   * after applying APK source mapping to it.
   */
  @Nullable
  private Navigatable getApkMappingNavigable(@NotNull CodeLocation location) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (LibraryMapping mapping : myLibraryMappings) {
      if (location.getFileName().startsWith(mapping.getOriginalPath())) {
        String pathTailAfterPrefix = location.getFileName().substring(mapping.getOriginalPath().length());
        String newFileName = Paths.get(mapping.getLocalPath(), pathTailAfterPrefix).toString();
        VirtualFile sourceFile = fileSystem.findFileByPath(newFileName);
        if (sourceFile != null && sourceFile.exists()) {
          return new OpenFileDescriptor(myProject, sourceFile, location.getLineNumber(), 0);
        }
      }
    }

    return null;
  }

  /** Get all the file mappings that connect the library in the APK with a build machine. */
  @NotNull
  private static List<LibraryMapping> getLibraryMappings(@NotNull Project project) {
    // Using a list to preserve order from getSymbolFolderPathMappings and imitate LLDB's behavior.
    List<LibraryMapping> sourceMap = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ApkFacet apkFacet = ApkFacet.getInstance(module);
      if (apkFacet != null) {
        for (Map.Entry<String, String> entry : apkFacet.getConfiguration().getSymbolFolderPathMappings().entrySet()) {
          // getSymbolFolderPathMappings() has a lot of path records which are not mapped, they need
          // to be filtered out.
          if (!entry.getValue().isEmpty() && !entry.getKey().equals(entry.getValue())) {
            sourceMap.add(new LibraryMapping(entry.getKey(), entry.getValue()));
          }
        }
      }
    }
    return sourceMap;
  }
}
