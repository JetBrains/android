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
package com.android.tools.idea.editors;

import com.android.tools.idea.editors.strings.StringsVirtualFile;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AndroidFakeFileSystem extends DummyFileSystem {
  @NonNls public static final String PROTOCOL = "android-dummy";
  public static final VirtualFileSystem INSTANCE = new AndroidFakeFileSystem();
  public static final char SEPARATOR = '/';

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    final List<String> components = Splitter.on(SEPARATOR).splitToList(path);
    final int size = components.size();

    // All files are of form: projectPath/[moduleName]/fileName, and thus
    // there should be at least three components in the result of splitting.
    if (size < 3) {
      return null;
    }

    final String projectPath = Joiner.on(SEPARATOR).join(components.subList(0, size - 2));
    final String moduleName = components.get(size - 2);
    final String fileName = components.get(size - 1);
    final Project project = findProject(projectPath);

    if (project == null) {
      return null;
    }

    if (StringsVirtualFile.NAME.equals(fileName)) {
      Module m = findModule(project, moduleName);
      if (m != null) {
        return StringsVirtualFile.getStringsVirtualFile(m);
      }
    }

    return null;
  }

  @NotNull
  public static String constructPathForFile(@NotNull String fileName, @NotNull Module module) {
    return Joiner.on(SEPARATOR).join(module.getProject().getBasePath(), module.getName(), fileName);
  }

  @NotNull
  public static String constructPathForFile(@NotNull String fileName, @NotNull Project project) {
    return Joiner.on(SEPARATOR).join(project.getBasePath(), "", fileName);
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String name) {
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      if (m.getName().equals(name)) {
        return m;
      }
    }

    return null;
  }

  @Nullable
  private static Project findProject(@NotNull String basePath) {
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      if (basePath.equals(p.getBasePath())) {
        return p;
      }
    }

    return null;
  }
}
