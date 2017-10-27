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
package com.android.tools.idea.resourceExplorer;

import com.android.tools.idea.editors.AndroidFakeFileSystem;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.io.URLUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Virtual file used to have resource editor in a separate tab, not tied to any particular
 * XML file with style definitions
 */
public class ResourceExplorerFile extends LightVirtualFile {
  public static final String FILENAME = "Resource Editor";
  private static final Key<ResourceExplorerFile> KEY = Key.create(ResourceExplorerFile.class.getName());
  private static final Key<Boolean> CACHE_LOOKUP_KEY = Key.create("cache_lookup_key");

  private VirtualFile myParent;
  private final String myPath;

  private ResourceExplorerFile(final @NotNull Project project) {
    super(FILENAME);
    myPath = AndroidFakeFileSystem.constructPathForFile(FILENAME, project);
  }

  @NotNull
  public static ResourceExplorerFile getResourceEditorFile(@NotNull Project project) {
    ResourceExplorerFile vFile = project.getUserData(KEY);
    if (vFile == null) {
      vFile = getResourceEditorVirtualFileFromCache(project);

      if (vFile == null) {
        vFile = new ResourceExplorerFile(project);
      }

      // If the ResourceEditorVirtualFile comes from the cache, it might have been created by another project,
      // since the cache is indexed by the file url. So we know that myPath will be accurate, but we need to
      // get the correct parent virtual file.
      vFile.myParent = project.getBaseDir();
      project.putUserData(KEY, vFile);
    }

    return vFile;
  }

  /**
   * This looks at the cache inside VirtualFilePointerManager (used for example too keep recent opened files)
   * to see if there already is a ResourceEditorVirtualFile that was created for this project. If there is one, return it.
   * Otherwise return null.
   */
  @Nullable
  private static ResourceExplorerFile getResourceEditorVirtualFileFromCache(@NotNull Project project) {
    if (project.getUserData(CACHE_LOOKUP_KEY) == null) {
      // Needed to avoid infinite loops since VirtualFilePointerManager.create calls getResourceEditorFile
      project.putUserData(CACHE_LOOKUP_KEY, true);
      String url = AndroidFakeFileSystem.INSTANCE.getProtocol() + URLUtil.SCHEME_SEPARATOR
                   + AndroidFakeFileSystem.constructPathForFile(FILENAME, project);
      // VirtualFilePointerManager does not have a get method, but the create method is equivalent to a get when the pointer exists
      VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(url, project, null);
      return (ResourceExplorerFile)pointer.getFile();
    }
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ResourceEditorFileType.INSTANCE;
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

  private static class ResourceEditorFileType extends FakeFileType {
    private ResourceEditorFileType() { }
    public static final ResourceEditorFileType INSTANCE = new ResourceEditorFileType();

    @Override
    public boolean isMyFileType(@NotNull final VirtualFile file) {
      return file.getFileType() instanceof ResourceEditorFileType;
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
      return StudioIcons.NewResourceFile.VERSION; // TODO placeholder icon, remove when we have an actual icon
    }

    @Override
    public boolean isBinary() {
      return false;
    }
  }
}
