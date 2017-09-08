/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.SdkConstants;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.project.BuildSystemService;
import com.android.tools.idea.project.BuildSystemServiceUtil;
import com.android.tools.idea.projectsystem.AndroidSourceSet;
import com.android.tools.idea.templates.Parameter;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AndroidGradleModuleUtils {

  /**
   * Convenience method to convert a {@link AndroidSourceSet} into a {@link SourceProvider}.
   * Note that this target source provider has many fields stubbed out and should
   * be used carefully.
   *
   * TODO: Investigate getting rid of dependencies on {@link SourceProvider} in
   * {@link Parameter#validate} as this may allow us to delete this code
   */
  @NotNull
  public static SourceProvider getSourceProvider(@NotNull AndroidSourceSet sourceSet) {
    return new SourceProviderAdapter(sourceSet.getName(), sourceSet.getPaths());
  }

  /**
   * Given a file and a project, return the Module corresponding to the containing Gradle project for the file.  If the file is not
   * contained by any project then return null
   */
  @Nullable
  public static Module getContainingModule(File file, Project project) {
    VirtualFile vFile = VfsUtil.findFileByIoFile(file, false);
    if (vFile == null) {
      return null;
    }
    Module bestMatch = null;
    int bestMatchValue = Integer.MAX_VALUE;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      GradleFacet facet = GradleFacet.getInstance(module);
      if (facet != null) {
        GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
        assert gradleModuleModel != null;
        VirtualFile buildFile = gradleModuleModel.getBuildFile();
        if (buildFile != null) {
          VirtualFile root = buildFile.getParent();
          if (VfsUtilCore.isAncestor(root, vFile, true)) {
            String relativePath = VfsUtilCore.getRelativePath(vFile, root, '/');
            if (relativePath != null) {
              int value = Iterables.size(Splitter.on('/').split(relativePath));
              if (value < bestMatchValue) {
                bestMatch = module;
                bestMatchValue = value;
              }
            }
          }
        }
      }
    }
    return bestMatch;
  }

  /**
   * Set the executable bit on the 'gradlew' wrapper script on Mac/Linux
   * On Windows, we use a separate gradlew.bat file which does not need an
   * executable bit.
   *
   * @throws IOException
   */
  public static void setGradleWrapperExecutable(@NotNull File projectRoot) throws IOException {
    if (SystemInfo.isUnix) {
      File gradlewFile = new File(projectRoot, SdkConstants.FN_GRADLE_WRAPPER_UNIX);
      if (!gradlewFile.isFile()) {
        throw new IOException("Could not find gradle wrapper. Command line builds may not work properly.");
      }
      FileUtil.setExecutableAttribute(gradlewFile.getPath(), true);
    }
  }
}
