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
package com.android.tools.idea.startup;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Helper code for attaching the external annotations .jar file
 * to the user's Android SDK platforms, if necessary
 */
@SuppressWarnings("SpellCheckingInspection") // "Modificator" in API usage
public class ExternalAnnotationsSupport {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotationsSupport.class);
  private static final String[] DEVELOPMENT_ANNOTATIONS_PATHS = {
    "/../adt/idea/android/annotations",
    "/android/android/annotations",
    "/community/android/android/annotations"
  };

  // Based on similar code in MagicConstantInspection
  @SuppressWarnings("ALL")
  private static void checkAnnotationsJarAttached(@NotNull PsiFile file, @NotNull ProblemsHolder holder) {
    // Not yet used
    if (false) {
    final Project project = file.getProject();
    PsiClass actionBar = JavaPsiFacade.getInstance(project).findClass("android.app.ActionBar", GlobalSearchScope.allScope(project));
    if (actionBar == null) {
      return; // no sdk to attach
    }
    PsiMethod[] methods = actionBar.findMethodsByName("getNavigationMode", false);
    if (methods.length != 1) {
      return; // no sdk to attach
    }
    PsiMethod getModifiers = methods[0];
    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    PsiAnnotation annotation = annotationsManager.findExternalAnnotation(getModifiers, MagicConstant.class.getName());
    if (annotation != null) {
      return;
    }
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(getModifiers);
    if (virtualFile == null) {
      return; // no sdk to attach
    }
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
    Sdk sdk = null;
    for (OrderEntry orderEntry : entries) {
      if (orderEntry instanceof JdkOrderEntry) {
        sdk = ((JdkOrderEntry)orderEntry).getJdk();
        if (sdk != null) {
          break;
        }
      }
    }
    if (sdk == null) {
      return; // no sdk to attach
    }
    final Sdk finalSdk = sdk;

    String path = finalSdk.getHomePath();
    String text = "No IDEA annotations attached to the Android SDK " + finalSdk.getName() + (path == null ? "" : " (" +
                   FileUtil.toSystemDependentName(path) + ")") + ", some issues will not be found";
    holder.registerProblem(file, text, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new LocalQuickFix() {
      @NotNull
      @Override
      public String getName() {
        return "Attach annotations";
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return getName();
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            SdkModificator modifier = finalSdk.getSdkModificator();
            attachJdkAnnotations(modifier);
            modifier.commitChanges();
          }
        });
      }
    });
    }
  }

  // Based on similar code in JavaSdkImpl
  public static void attachJdkAnnotations(@NotNull SdkModificator modificator) {
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());
    VirtualFileManager fileManager = VirtualFileManager.getInstance();

    // release build?
    String releaseLocation = homePath + "/plugins/android/lib/androidAnnotations.jar";
    VirtualFile root = fileManager.findFileByUrl("jar://" + releaseLocation + "!/");

    for (String relativePath : DEVELOPMENT_ANNOTATIONS_PATHS) {
      if (root != null) break;
      String developmentLocation = homePath + relativePath;
      root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(developmentLocation));
    }

    if (root == null) {
      // error message tailored for release build file layout
      LOG.error("jdk annotations not found in: " + releaseLocation);
      return;
    }

    OrderRootType annoType = AnnotationOrderRootType.getInstance();
    modificator.removeRoot(root, annoType);
    modificator.addRoot(root, annoType);
  }

  public static void addAnnotations(@NotNull Sdk sdk) {
    SdkModificator modifier = sdk.getSdkModificator();
    attachJdkAnnotations(modifier);
    modifier.commitChanges();
  }

  public static void addAnnotationsIfNecessary(@NotNull Sdk sdk) {
    // Attempt to insert SDK annotations
    VirtualFile[] roots = sdk.getRootProvider().getFiles(AnnotationOrderRootType.getInstance());
    if (roots.length > 0) {
      return;
    }
    SdkModificator modifier = sdk.getSdkModificator();
    attachJdkAnnotations(modifier);
    modifier.commitChanges();
  }
}
