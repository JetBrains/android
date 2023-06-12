/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.resourceManagers;

import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import com.android.tools.dom.attrs.AttributeDefinitions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ResourceManager {
  protected final Project myProject;

  protected ResourceManager(@NotNull Project project) {
    myProject = project;
  }

  /** Returns true if the given directory is a resource directory in this module. */
  public abstract boolean isResourceDir(@NotNull VirtualFile dir);

  @Nullable
  public abstract AttributeDefinitions getAttributeDefinitions();

  @Nullable
  public ResourceFolderType getFileResourceFolderType(@NotNull PsiFile file) {
    return ApplicationManager.getApplication().runReadAction((Computable<ResourceFolderType>)() -> {
      PsiDirectory dir = file.getContainingDirectory();
      if (dir == null) {
        return null;
      }

      PsiDirectory possibleResDir = dir.getParentDirectory();
      if (possibleResDir == null || !isResourceDir(possibleResDir.getVirtualFile())) {
        return null;
      }
      return ResourceFolderType.getFolderType(dir.getName());
    });
  }

  @Nullable
  public String getFileResourceType(@NotNull PsiFile file) {
    ResourceFolderType folderType = getFileResourceFolderType(file);
    return folderType == null ? null : folderType.getName();
  }

  @NotNull
  protected abstract Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories();
}
