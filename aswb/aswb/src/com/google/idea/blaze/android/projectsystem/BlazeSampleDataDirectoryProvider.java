/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import static com.android.SdkConstants.FD_SAMPLE_DATA;
import static com.android.tools.idea.util.FileExtensions.toPathString;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.Nullable;

/**
 * A Blaze-specific implementation of {@link SampleDataDirectoryProvider} which ensures that the
 * sample data directory of an {@link
 * com.google.idea.blaze.android.sync.model.AndroidResourceModule} sits parallel to the module's res
 * folder, rather than inside it.
 *
 * <p>This class is necessary for Blaze projects because the main content root which {@link
 * com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider} would use to house the
 * sample data directory of a resource module is the res folder itself, and Blaze sync prohibits us
 * from creating unexpected sub-directories in the res folder.
 */
public final class BlazeSampleDataDirectoryProvider implements SampleDataDirectoryProvider {
  private Module module;
  private boolean isResourceModule;

  public BlazeSampleDataDirectoryProvider(Module module) {
    this.module = module;
    AndroidResourceModuleRegistry resourceModuleRegistry =
        AndroidResourceModuleRegistry.getInstance(module.getProject());
    isResourceModule = resourceModuleRegistry.get(module) != null;
  }

  @Override
  @Nullable
  public PathString getSampleDataDirectory() {
    // If this isn't a resource module then it doesn't have any layouts
    // that we could open in the layout editor anyway, so there's no
    // reason for a sample data directory.
    if (!isResourceModule) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    VirtualFile mainContentRoot = facet != null ? AndroidRootUtil.getMainContentRoot(facet) : null;
    if (mainContentRoot == null) {
      return null;
    }

    // The main content root of a resource module is its res folder.
    // Instead, we'll use the res folder's parent directory so that the
    // sample data directory and the res folder sit parallel to one another.
    VirtualFile parentDir = getSampleDataDirectoryHomeForResFolder(mainContentRoot);
    if (parentDir == null) {
      return null;
    }

    return toPathString(parentDir).resolve(FD_SAMPLE_DATA);
  }

  @Override
  @Nullable
  public PathString getOrCreateSampleDataDirectory() throws IOException {
    PathString sampleDataDirectory = getSampleDataDirectory();
    if (sampleDataDirectory == null) {
      return null;
    }

    PathString rootPath = sampleDataDirectory.getRoot();
    VirtualFile root = rootPath != null ? toVirtualFile(rootPath) : null;
    if (root == null) {
      return sampleDataDirectory;
    }

    createDirectoryIfMissing(root, sampleDataDirectory.getPortablePath());

    // We want to make sure that the sample data directory is associated with this resource module
    // instead of just being lumped into the workspace module because it sits outside the res
    // folder.
    ModuleRootModificationUtil.addContentRoot(module, sampleDataDirectory.getPortablePath());
    return sampleDataDirectory;
  }

  @Nullable
  private static VirtualFile getSampleDataDirectoryHomeForResFolder(VirtualFile resourceFolder) {
    return resourceFolder.getParent();
  }

  /**
   * Given a module's resource folder, returns the sample data directory of the module. This method
   * is used during Blaze sync to determine if a module's sample data directory exists (in which
   * case sync will need to add it as one of the module's content roots).
   *
   * @param resourceFolder the module's resource folder
   * @return the sample data directory of the module to which the resource folder belongs, or null
   *     if it can't be found.
   */
  @Nullable
  public static VirtualFile getSampleDataDirectoryForResFolder(VirtualFile resourceFolder) {
    VirtualFile parentDir = getSampleDataDirectoryHomeForResFolder(resourceFolder);
    if (parentDir == null) {
      return null;
    }

    return parentDir.findChild(FD_SAMPLE_DATA);
  }
}
