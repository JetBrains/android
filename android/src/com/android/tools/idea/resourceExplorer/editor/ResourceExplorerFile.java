/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.editor;

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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Virtual file used to have resource editor in a separate tab, not tied to any particular
 * XML file with style definitions.
 */
public class ResourceExplorerFile extends LightVirtualFile {
  public static final String FILENAME = "Resource Editor";
  private static final Key<ResourceExplorerFile> KEY = Key.create(ResourceExplorerFile.class.getName());
  private static final Key<Boolean> CACHE_LOOKUP_KEY = Key.create("cache_lookup_key");
  private final AndroidFacet myFacet;

  private VirtualFile myParent;
  private final String myPath;

  private ResourceExplorerFile(final @NotNull AndroidFacet facet) {
    super(FILENAME);
    myPath = AndroidFakeFileSystem.constructPathForFile(FILENAME, facet.getModule());
    myFacet = facet;
  }

  @NotNull
  public static ResourceExplorerFile getResourceEditorFile(@NotNull Project project, AndroidFacet facet) {
    ResourceExplorerFile vFile = facet.getUserData(KEY);
    if (vFile == null) {
      vFile = getResourceEditorVirtualFileFromCache(facet);

      if (vFile == null) {
        vFile = new ResourceExplorerFile(facet);
      }

      // If the ResourceEditorVirtualFile comes from the cache, it might have been created by another project,
      // since the cache is indexed by the file url. So we know that myPath will be accurate, but we need to
      // get the correct parent virtual file.
      vFile.myParent = project.getBaseDir();
      facet.putUserData(KEY, vFile);
    }

    return vFile;
  }

  /**
   * This looks at the cache inside VirtualFilePointerManager (used for example too keep recent opened files)
   * to see if there already is a ResourceEditorVirtualFile that was created for this project. If there is one, return it.
   * Otherwise return null.
   */
  @Nullable
  private static ResourceExplorerFile getResourceEditorVirtualFileFromCache(@NotNull AndroidFacet facet) {
    if (facet.getUserData(CACHE_LOOKUP_KEY) == null) {
      // Needed to avoid infinite loops since VirtualFilePointerManager.create calls getResourceEditorFile
      facet.putUserData(CACHE_LOOKUP_KEY, true);
      String url = AndroidFakeFileSystem.INSTANCE.getProtocol() + URLUtil.SCHEME_SEPARATOR
                   + AndroidFakeFileSystem.constructPathForFile(FILENAME, facet.getModule());
      // VirtualFilePointerManager does not have a get method, but the create method is equivalent to a get when the pointer exists
      VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(url, facet, null);
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
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  @Override
  public String getPath() {
    return myPath;
  }

  private static class ResourceEditorFileType extends FakeFileType {

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
