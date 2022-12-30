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

import static com.android.SdkConstants.FD_DATA;
import static com.android.SdkConstants.FD_PLATFORMS;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static java.io.File.separator;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.util.StudioPathManager;
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
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.annotations.NotNull;

/**
 * Helper code for attaching the external annotations .jar file
 * to the user's Android SDK platforms, if necessary
 */
@SuppressWarnings("SpellCheckingInspection") // "Modificator" in API usage
public final class ExternalAnnotationsSupport {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotationsSupport.class);

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
                   FileUtilRt.toSystemDependentName(path) + ")") + ", some issues will not be found";
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
    VirtualFileManager fileManager = VirtualFileManager.getInstance();
    VirtualFile root = null;

    // If using Android 28 or higher (technically, revision as of revision 5 of the P SDK),
    // the annotations bundled with the SDK is better. Try to look up the SDK version
    // and use it if >= 28r5.
    if (modificator.getSdkAdditionalData() instanceof AndroidSdkAdditionalData) {
      AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)modificator.getSdkAdditionalData();
      String platformHash = additionalData.getBuildTargetHashString();
      if (platformHash != null) {
        String sdkRootPath = modificator.getHomePath();
        if (sdkRootPath != null) {
          Path sdkRoot = Paths.get(sdkRootPath);
          if (Files.isDirectory(sdkRoot)) {
            ProgressIndicator progress = new StudioLoggerProgressIndicator(ExternalAnnotationsSupport.class);
            AndroidSdkHandler sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, sdkRoot);
            LocalPackage info = sdkHandler.getLocalPackage(FD_PLATFORMS + ";" + platformHash, progress);
            if (info != null) {
              Revision revision = info.getVersion();
              if (info.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType) {
                DetailsTypes.PlatformDetailsType details = (DetailsTypes.PlatformDetailsType)info.getTypeDetails();
                if (details.getApiLevel() >= 29 || details.getApiLevel() == 28 && revision.getMajor() >= 5) {
                  // Yes, you're using Android P, DP5 or later: The best annotations are bundled with the SDK
                  String releaseLocation = info.getLocation().toString() + separator + FD_DATA + separator + FN_ANNOTATIONS_ZIP;
                  root = fileManager.findFileByUrl("jar://" + FileUtil.toSystemIndependentName(releaseLocation) + "!/");
                }
              }
            }
          }
        }
      }
    }

    if (root == null) {
      String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());

      // release build? If so the jar file is bundled under android/lib..
      String releaseLocation = homePath + "/plugins/android/resources/androidAnnotations.jar";
      root = fileManager.findFileByUrl("jar://" + releaseLocation + "!/");

      if (root == null) {
        // Otherwise, in development tree. Look both in Studio and IJ source tree locations.
        final String[] paths = {
          StudioPathManager.isRunningFromSources() ?
          StudioPathManager.resolvePathFromSourcesRoot("tools/adt/idea/android/annotations").toString() :
          null,
          FileUtil.join(homePath, "android/android/annotations"),
          FileUtil.join(homePath, "community/android/android/annotations")
        };
        for (String relativePath : paths) {
          if (relativePath == null) continue;
          if (root != null) break;
          root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(relativePath));
        }
      }

      if (root == null) {
        // error message tailored for release build file layout
        LOG.error("jdk annotations not found in: " + releaseLocation);
        return;
      }
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
