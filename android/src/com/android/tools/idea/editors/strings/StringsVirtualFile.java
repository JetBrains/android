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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.AndroidFakeFileSystem;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import icons.AndroidIcons;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StringsVirtualFile extends LightVirtualFile {
  public static final String NAME = "Translations Editor";

  private static final Key<StringsVirtualFile> KEY = Key.create(StringsVirtualFile.class.getName());
  @NotNull private final AndroidFacet myFacet;

  private StringsVirtualFile(@NotNull AndroidFacet facet) {
    super(NAME, StringsResourceFileType.INSTANCE, "");
    myFacet = facet;
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Override
  public VirtualFile getParent() {
    // Returns the module folder as the parent of this file. This is only so that the breadcrumb at the top looks like
    // "project > module > Translations Editor" instead of just "Translations Editor"
    VirtualFile moduleFile = myFacet.getModule().getModuleFile();
    return moduleFile == null ? null : moduleFile.getParent();
  }

  @NotNull
  @Override
  public String getPath() {
    Module module = myFacet.getModule();
    return module.getProject().isDisposed() ? super.getPath() : AndroidFakeFileSystem.constructPathForFile(getName(), module);
  }

  @Nullable
  public static StringsVirtualFile getInstance(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    return module == null ? null : getStringsVirtualFile(module);
  }

  @Nullable
  public static StringsVirtualFile getStringsVirtualFile(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    StringsVirtualFile file = facet.getUserData(KEY);
    if (file == null) {
      file = new StringsVirtualFile(facet);
      facet.putUserData(KEY, file);
    }

    return file;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return AndroidFakeFileSystem.INSTANCE;
  }

  private static class StringsResourceFileType extends FakeFileType {
    public static final StringsResourceFileType INSTANCE = new StringsResourceFileType();

    private StringsResourceFileType() {
    }

    @Override
    public boolean isMyFileType(@NotNull VirtualFile file) {
      return file.getFileType() instanceof StringsResourceFileType;
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }

    @NotNull
    @Override
    public String getDescription() {
      return "";
    }

    @Override
    public Icon getIcon() {
      return StudioIcons.LayoutEditor.Toolbar.LANGUAGE;
    }
  }
}
