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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.io.File;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class GradleFiles {
  private static final Key<Boolean> EXTERNAL_BUILD_FILES_MODIFIED = Key.create("android.gradle.project.external.build.files.modified");

  @NotNull private final Project myProject;
  @NotNull private final FileDocumentManager myDocumentManager;

  @NotNull
  public static GradleFiles getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleFiles.class);
  }

  public GradleFiles(@NotNull Project project, @NotNull FileDocumentManager documentManager) {
    myProject = project;
    myDocumentManager = documentManager;
  }

  /**
   * Indicates whether a project sync with Gradle is needed if the following files:
   * <ul>
   *   <li>gradle.properties</li>
   *   <li>build.gradle</li>
   *   <li>settings.gradle</li>
   *   <li>external build files (e.g. cmake files)</li>
   * </ul>
   * were modified after the given time.
   *
   * @param referenceTimeInMillis the given time, in milliseconds.
   * @return {@code true} if any of the Gradle files changed, {@code false} otherwise.
   * @throws IllegalArgumentException if the given time is less than or equal to zero.
   */
  public boolean areGradleFilesModified(long referenceTimeInMillis) {
    if (referenceTimeInMillis <= 0) {
      throw new IllegalArgumentException("Reference time (in milliseconds) should be greater than zero");
    }
    setExternalBuildFilesModified(false);

    if (areFilesInProjectRootFolderModified(referenceTimeInMillis, FN_GRADLE_PROPERTIES, FN_SETTINGS_GRADLE)) {
      return true;
    }

    GradleWrapper gradleWrapper = GradleWrapper.find(myProject);
    if (gradleWrapper != null) {
      File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
      if (propertiesFilePath.exists()) {
        long modified = propertiesFilePath.lastModified();
        if (modified > referenceTimeInMillis) {
          return true;
        }
        VirtualFile propertiesFile = gradleWrapper.getPropertiesFile();
        if (propertiesFile != null && myDocumentManager.isFileModified(propertiesFile)) {
          return true;
        }
      }
    }

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      VirtualFile buildFile = getGradleBuildFile(module);
      if (buildFile != null) {
        if (myDocumentManager.isFileModified(buildFile)) {
          return true;
        }
        File buildFilePath = virtualToIoFile(buildFile);
        if (buildFilePath.lastModified() > referenceTimeInMillis) {
          return true;
        }
      }

      NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
      if (ndkModuleModel != null) {
        for (File externalBuildFile : ndkModuleModel.getAndroidProject().getBuildFiles()) {
          if (externalBuildFile.lastModified() > referenceTimeInMillis) {
            setExternalBuildFilesModified(true);
            return true;
          }
          // TODO find a better way to find a VirtualFile without refreshing the file systerm. It is expensive.
          VirtualFile virtualFile = findFileByIoFile(externalBuildFile, true);
          if (virtualFile != null && myDocumentManager.isFileModified(virtualFile)) {
            setExternalBuildFilesModified(true);
            return true;
          }
        }
      }
    }

    return false;
  }

  private boolean areFilesInProjectRootFolderModified(long referenceTimeInMillis, @NotNull String... fileNames) {
    File rootFolderPath = getBaseDirPath(myProject);
    for (String fileName : fileNames) {
      File filePath = new File(rootFolderPath, fileName);
      if (filePath.exists()) {
        long modified = filePath.lastModified();
        if (modified > referenceTimeInMillis) {
          return true;
        }
        VirtualFile rootFolder = myProject.getBaseDir();
        assert rootFolder != null;
        VirtualFile virtualFile = rootFolder.findChild(fileName);
        if (virtualFile != null && myDocumentManager.isFileModified(virtualFile)) {
          return true;
        }
      }
    }
    return false;
  }

  private void setExternalBuildFilesModified(boolean changed) {
    myProject.putUserData(EXTERNAL_BUILD_FILES_MODIFIED, changed ? true : null);
  }

  public boolean areExternalBuildFilesModified() {
    return EXTERNAL_BUILD_FILES_MODIFIED.get(myProject, false);
  }

  public boolean isGradleFile(@NotNull PsiFile psiFile) {
    if (psiFile.getFileType() == GroovyFileType.GROOVY_FILE_TYPE) {
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null && EXT_GRADLE.equals(file.getExtension())) {
        return true;
      }
    }
    if (psiFile.getFileType() == PropertiesFileType.INSTANCE) {
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null && (FN_GRADLE_PROPERTIES.equals(file.getName()) || FN_GRADLE_WRAPPER_PROPERTIES.equals(file.getName()))) {
        return true;
      }
    }

    return false;
  }
}
