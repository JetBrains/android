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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.gradle.eclipse.GradleImport;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Project import logic used by UI elements
 */
public final class ProjectImportUtil {
  @NonNls private static final String ANDROID_NATURE_NAME = "com.android.ide.eclipse.adt.AndroidNature";

  private static final Logger LOG = Logger.getInstance(ProjectImportUtil.class);

  private ProjectImportUtil() {
    // Do not instantiate
  }

  @VisibleForTesting
  @Nullable
  public static VirtualFile findImportTarget(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      VirtualFile target = findMatchingChild(file, SdkConstants.FN_BUILD_GRADLE, SdkConstants.FN_SETTINGS_GRADLE);
      if (target != null) {
        return target;
      }
      target = findMatchingChild(file, GradleImport.ECLIPSE_DOT_PROJECT);
      if (target != null) {
        return findImportTarget(target);
      }
    }
    else {
      if (GradleImport.ECLIPSE_DOT_PROJECT.equals(file.getName()) && hasAndroidNature(file)) {
        return file;
      }
      if (GradleImport.ECLIPSE_DOT_CLASSPATH.equals(file.getName())) {
        return findImportTarget(file.getParent());
      }
    }
    return file;
  }

  @Nullable
  private static VirtualFile findMatchingChild(@NotNull VirtualFile parent, @NotNull String... validNames) {
    if (parent.isDirectory()) {
      for (VirtualFile child : parent.getChildren()) {
        for (String name : validNames) {
          if (name.equals(child.getName())) {
            return child;
          }
        }
      }
    }
    return null;
  }

  private static boolean hasAndroidNature(@NotNull VirtualFile projectFile) {
    File dotProjectFile = new File(projectFile.getPath());
    try {
      Element naturesElement = JDOMUtil.loadDocument(dotProjectFile).getRootElement().getChild("natures");
      if (naturesElement != null) {
        List<Element> naturesList = naturesElement.getChildren("nature");
        for (Element nature : naturesList) {
          String natureName = nature.getText();
          if (ANDROID_NATURE_NAME.equals(natureName)) {
            return true;
          }
        }
      }
    }
    catch (Exception e) {
      LOG.info(String.format("Unable to get natures for Eclipse project file '%1$s", projectFile.getPath()), e);
    }
    return false;
  }

  @NotNull
  public static ImportSourceKind getImportLocationKind(@NotNull VirtualFile file) {
    VirtualFile target = findImportTarget(file);
    if (target == null) {
      return ImportSourceKind.NOTHING;
    }
    // Prioritize ADT importer
    VirtualFile targetDir = target.isDirectory() ? target : target.getParent();
    File targetDirFile = VfsUtilCore.virtualToIoFile(targetDir);
    if (GradleImport.isAdtProjectDir(targetDirFile) && targetDir.findChild(SdkConstants.FN_BUILD_GRADLE) == null) {
      return ImportSourceKind.ADT;
    }
    if (GradleImport.isEclipseProjectDir(targetDirFile) && targetDir.findChild(SdkConstants.FN_BUILD_GRADLE) == null) {
      return ImportSourceKind.ECLIPSE;
    }
    if (GradleConstants.EXTENSION.equals(target.getExtension())) {
      return ImportSourceKind.GRADLE;
    }
    return ImportSourceKind.OTHER;
  }

  public static Map<String, VirtualFile> findModules(final @NotNull VirtualFile vfile,
                                                     final @NotNull Project project) throws IOException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ImportSourceKind kind = getImportLocationKind(vfile);
    if (kind == ImportSourceKind.GRADLE) {
      return GradleProjectImporter.getInstance().getRelatedProjects(vfile, project);
    } else if (kind == ImportSourceKind.ADT) {
      GradleImport gradleImport = new GradleImport();
      gradleImport.importProjects(Collections.singletonList(VfsUtilCore.virtualToIoFile(vfile)));
      Map<String, File> adtProjects = gradleImport.getDetectedModuleLocations();
      return Maps.transformValues(adtProjects, new Function<File, VirtualFile>() {
        @Override
        public VirtualFile apply(File input) {
          return input == null ? null : VfsUtil.findFileByIoFile(input, true);
        }
      });
    } else {
      return Collections.emptyMap();
    }

  }
}
