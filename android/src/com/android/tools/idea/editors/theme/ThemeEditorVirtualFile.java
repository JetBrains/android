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
package com.android.tools.idea.editors.theme;

import com.android.tools.idea.editors.AndroidFakeFileSystem;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Virtual file used to have theme editor is separate tab, not tied to any particular
 * XML file with style definitions
 */
public class ThemeEditorVirtualFile extends LightVirtualFile {
  public static final String FILENAME = "Theme Editor";
  private static final Key<ThemeEditorVirtualFile> KEY = Key.create(ThemeEditorVirtualFile.class.getName());

  private final VirtualFile myParent;
  private final String myPath;

  private ThemeEditorVirtualFile(final @NotNull Project project) {
    super(FILENAME);
    myParent = project.getBaseDir();
    myPath = AndroidFakeFileSystem.constructPathForFile(FILENAME, project);
  }

  @NotNull
  public static ThemeEditorVirtualFile getThemeEditorFile(@NotNull Project project) {
    ThemeEditorVirtualFile vfile = project.getUserData(KEY);
    if (vfile == null) {
      vfile = new ThemeEditorVirtualFile(project);
      project.putUserData(KEY, vfile);
    }

    return vfile;
  }

  @Nullable
  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ThemeEditorFileType.INSTANCE;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return AndroidFakeFileSystem.INSTANCE;
  }

  @NotNull
  @Override
  public String getPath() {
    return myPath;
  }

  private static class ThemeEditorFileType extends FakeFileType {
    private ThemeEditorFileType() { }
    public static final ThemeEditorFileType INSTANCE = new ThemeEditorFileType();

    @Override
    public boolean isMyFileType(@NotNull final VirtualFile file) {
      return file.getFileType() instanceof ThemeEditorFileType;
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
      return AndroidIcons.Themes;
    }
  }
}
