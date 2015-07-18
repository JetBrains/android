/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.quickfix;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
  * Quickfix to add dependency to another library in gradle.build file and sync the project.
  */
public class AddGradleLibraryDependencyFix extends GradleDependencyFix {
  @NotNull private final LibraryOrderEntry myLibraryEntry;
  @NotNull private final Module myCurrentModule;
  @NotNull private final PsiClass myClass;
  @NotNull private final PsiReference myReference;
  @Nullable private final String myLibraryGradleEntry;

  public AddGradleLibraryDependencyFix(@NotNull LibraryOrderEntry libraryEntry, @NotNull Module currentModule, @NotNull PsiClass aCLass,
                                       @NotNull PsiReference reference) {
    myLibraryEntry = libraryEntry;
    myCurrentModule = currentModule;
    myClass = aCLass;
    myReference = reference;
    myLibraryGradleEntry = getLibraryGradleEntry();
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("orderEntry.fix.add.library.to.classpath", myLibraryGradleEntry);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    return !project.isDisposed() && !myCurrentModule.isDisposed() && myLibraryEntry.isValid() && myLibraryGradleEntry != null;
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable final Editor editor, @Nullable PsiFile file) {
    if (myLibraryGradleEntry == null) {
      return;
    }
    final Dependency dependency = new Dependency(getDependencyScope(myCurrentModule, false), Dependency.Type.EXTERNAL,
                                                 myLibraryGradleEntry);

    final Application application = ApplicationManager.getApplication();
    application.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        application.runWriteAction(new Runnable() {
          @Override
          public void run() {
            addDependency(myCurrentModule, dependency);
            gradleSyncAndImportClass(myCurrentModule, editor, myReference, new Function<Void, List<PsiClass>>() {
              @Override
              public List<PsiClass> apply(@Nullable Void input) {
                return ImmutableList.of(myClass);
              }
            });
          }
        });
      }
    }, application.getDefaultModalityState());
  }

  /**
   * Given a library entry, find out its corresponded gradle dependency entry like 'group:name:version".
   */
  @Nullable
  private String getLibraryGradleEntry() {
    AndroidFacet androidFacet = AndroidFacet.getInstance(myLibraryEntry.getOwnerModule());

    String result = null;
    if (androidFacet != null) {
      result = getLibraryGradleEntryByAndroidFacet(androidFacet);
    }
    if (result == null) {
      result = getLibraryGradleEntryByExaminingPath();
    }
    return result;
  }

  @Nullable
  private String getLibraryGradleEntryByAndroidFacet(@NotNull AndroidFacet androidFacet) {
    IdeaAndroidProject androidProject = androidFacet.getAndroidModel();
    if (androidProject == null) {
      return null;
    }

    Variant selectedVariant = androidProject.getSelectedVariant();
    BaseArtifact testArtifact = androidProject.findSelectedTestArtifactInSelectedVariant();

    Library matchedLibrary = null;
    if (testArtifact != null) {
      matchedLibrary = findMatchedibrary(testArtifact);
    }
    if (matchedLibrary == null) {
      matchedLibrary = findMatchedibrary(selectedVariant.getMainArtifact());
    }
    if (matchedLibrary == null) {
      return null;
    }

    // TODO use getRequestedCoordinates once the interface is fixed.
    MavenCoordinates mavenCoordinates = matchedLibrary.getResolvedCoordinates();
    if (mavenCoordinates == null) {
      return null;
    }
    return mavenCoordinates.getGroupId() + ":" + mavenCoordinates.getArtifactId() + ":" + mavenCoordinates.getVersion();
  }

  @Nullable
  Library findMatchedibrary(@NotNull BaseArtifact artifact) {
    for (JavaLibrary library : artifact.getDependencies().getJavaLibraries()) {
      String libraryName = FileUtil.getNameWithoutExtension(library.getJarFile());
      if (libraryName.equals(myLibraryEntry.getLibraryName())) {
        return library;
      }
    }
    return null;
  }

  /**
   * Gradle dependencies are stored in following path:  xxx/:groupId/:artifactId/:version/xxx/:artifactId-:version.jar
   * therefor, if we can't get the artifact information from model, then try to extract from path.
   */
  @Nullable
  private String getLibraryGradleEntryByExaminingPath() {
    VirtualFile file = myLibraryEntry.getFiles(OrderRootType.CLASSES)[0];
    String libraryName = myLibraryEntry.getLibraryName();
    if (libraryName == null) {
      return null;
    }
    String[] splittedPath = file.getPath().split(System.getProperty("file.separator"));

    for (int i = 1; i < splittedPath.length - 2; i++) {
      if (libraryName.startsWith(splittedPath[i])) {
        String groupId = splittedPath[i - 1];
        String artifactId = splittedPath[i];
        String version = splittedPath[i + 1];
        if (libraryName.endsWith(version)) {
          return groupId + ":" + artifactId + ":" + version;
        }
      }
    }
    return null;
  }
}
