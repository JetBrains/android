/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.annotations.Nullable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

/**
 * A reference to a particular file in the project
 */
public class IncludeReference {
  /**
   * The unique id referencing the file, such as (for res/layout-land/main.xml)
   * "layout-land/main")
   */
  @NotNull
  private final String myId;

  /**
   * The project containing the file
   */
  @NotNull
  private final Project myProject;

  /**
   * The resource name of the file, such as (for res/layout/main.xml) "main"
   */
  @Nullable
  private String myName;

  /**
   * Creates a new include reference
   */
  private IncludeReference(@NotNull Project project, @NotNull String id) {
    myProject = project;
    myId = id;
  }

  /**
   * Returns the id identifying the given file within the project
   *
   * @return the id identifying the given file within the project
   */
  @NotNull
  public String getId() {
    return myId;
  }

  /**
   * Returns the {@link IFile} in the project for the given file. May return null if
   * there is an error in locating the file or if the file no longer exists.
   *
   * @return the project file, or null
   */
  @Nullable
  public VirtualFile getFile() {
    String reference = myId;
    if (reference.indexOf('/') == -1) {
      reference = FD_RES_LAYOUT + '/' + reference;
    }

    String projectPath = FD_RESOURCES + '/' + reference + '.' + EXT_XML;
    VirtualFile file = myProject.getBaseDir().findFileByRelativePath(projectPath);
    if (file != null && file.exists()) {
      return file;
    }

    return null;
  }

  /**
   * Returns a description of this reference, suitable to be shown to the user
   *
   * @return a display name for the reference
   */
  @NotNull
  public String getDisplayName() {
    // The ID is deliberately kept in a pretty user-readable format but we could
    // consider prepending layout/ on ids that don't have it (to make the display
    // more uniform) or ripping out all layout[-constraint] prefixes out and
    // instead prepending @ etc.
    return myId;
  }

  /**
   * Returns the name of the reference, suitable for resource lookup. For example,
   * for "res/layout/main.xml", as well as for "res/layout-land/main.xml", this
   * would be "main".
   *
   * @return the resource name of the reference
   */
  @NotNull
  public String getName() {
    if (myName == null) {
      myName = myId;
      int index = myName.lastIndexOf('/');
      if (index != -1) {
        myName = myName.substring(index + 1);
      }
    }

    return myName;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + myId.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IncludeReference other = (IncludeReference)obj;
    if (!myId.equals(other.myId)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "IncludeReference [getId()=" + getId() //$NON-NLS-1$
           + ", getDisplayName()=" + getDisplayName() //$NON-NLS-1$
           + ", getName()=" + getName() //$NON-NLS-1$
           + ", getFile()=" + getFile() + "]"; //$NON-NLS-1$
  }

  /**
   * Returns the resource name of this layout, such as {@code @layout/foo}.
   *
   * @return the resource name
   */
  @NotNull
  public String getResourceName() {
    return '@' + FD_RES_LAYOUT + '/' + getName();
  }
}
