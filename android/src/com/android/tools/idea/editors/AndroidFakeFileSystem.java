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
import com.intellij.util.containers.ContainerUtil;
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
    List<String> components = ContainerUtil.collect(Splitter.on(SEPARATOR).split(path).iterator());
    if (components.size() != 3) { // all files are of form: project/module/name
      return null;
    }

    Module m = findModule(findProject(components.get(0)), components.get(1));
    if (m == null) {
      return null;
    }

    String name = components.get(2);
    if (StringsVirtualFile.NAME.equals(name)) {
      return StringsVirtualFile.getStringsVirtualFile(m);
    }

    return null;
  }

  @NotNull
  public static String constructPathForFile(@NotNull String fileName, @NotNull Module module) {
    return Joiner.on(SEPARATOR).join(module.getProject().getName(), module.getName(), fileName);
  }

  @Nullable
  private static Module findModule(@Nullable Project project, @NotNull String name) {
    if (project == null) {
      return null;
    }

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      if (m.getName().equals(name)) {
        return m;
      }
    }

    return null;
  }

  @Nullable
  private static Project findProject(@NotNull String name) {
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      if (p.getName().equals(name)) {
        return p;
      }
    }

    return null;
  }
}
