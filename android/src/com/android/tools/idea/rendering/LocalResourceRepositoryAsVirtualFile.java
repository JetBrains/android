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
package com.android.tools.idea.rendering;

import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LocalResourceRepositoryAsVirtualFile extends LightVirtualFile {
  private final LocalResourceRepository myRepository;
  private String myName;

  @Nullable
  public static LocalResourceRepositoryAsVirtualFile getInstance(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    return ProjectResourceRepository.getProjectResources(facet, true).asVirtualFile();
  }

  LocalResourceRepositoryAsVirtualFile(@NotNull LocalResourceRepository repository) {
    super("", new LocalResourceRepositoryFileType(), "");
    myRepository = repository;
    myName = myRepository.getDisplayName();
  }

  public void setIcon(Icon icon) {
    if (getAssignedFileType() != null) {
      ((LocalResourceRepositoryFileType)getAssignedFileType()).setIcon(icon);
    }
  }

  @NotNull
  public LocalResourceRepository getRepository() {
    return myRepository;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public long getTimeStamp() {
    return myRepository.getModificationCount();
  }

  private static class LocalResourceRepositoryFileType extends FakeFileType {
    private Icon myIcon;

    public LocalResourceRepositoryFileType() {
      myIcon = AndroidIcons.Android;
    }

    @NotNull
    @Override
    public String getName() {
      return "Android Local Resource Repository";
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Android Local Resource Repository Files";
    }

    public void setIcon(@NotNull Icon icon) {
      myIcon = icon;
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public boolean isMyFileType(VirtualFile file) {
      return file instanceof LocalResourceRepositoryAsVirtualFile;
    }
  }
}
