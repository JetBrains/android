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
package com.android.tools.idea.gradle.quickfix;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.splitPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Quickfix to add dependency to another library in gradle.build file and sync the project.
 */
public class AddGradleLibraryDependencyFix extends AbstractGradleDependencyFix {
  @NotNull private final LibraryOrderEntry myLibraryEntry;
  @NotNull private final PsiClass myClass;

  @Nullable private final String myLibraryGradleEntry;

  public AddGradleLibraryDependencyFix(@NotNull LibraryOrderEntry libraryEntry,
                                       @NotNull PsiClass aCLass,
                                       @NotNull Module module,
                                       @NotNull PsiReference reference) {
    super(module, reference);
    myLibraryEntry = libraryEntry;
    myClass = aCLass;
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
    return !project.isDisposed() && !myModule.isDisposed() && myLibraryEntry.isValid() && myLibraryGradleEntry != null;
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable final Editor editor, @Nullable PsiFile file) {
    if (isEmpty(myLibraryGradleEntry)) {
      return;
    }
    final String configurationName = getConfigurationName(myModule, false);
    runWriteCommandActionAndSync(project, new Runnable() {
      @Override
      public void run() {
        addDependency(myModule, configurationName, myLibraryGradleEntry);
      }
    }, editor, new Callable<PsiClass[]>() {
      @Override
      public PsiClass[] call() {
        return new PsiClass[]{myClass};
      }
    });
  }

  /**
   * Given a library entry, find out its corresponded gradle dependency entry like 'group:name:version".
   */
  @Nullable
  private String getLibraryGradleEntry() {
    AndroidFacet androidFacet = AndroidFacet.getInstance(myLibraryEntry.getOwnerModule());

    String result = null;
    if (androidFacet != null) {
      result = getLibraryGradleEntry(androidFacet);
    }
    if (result == null) {
      result = getLibraryGradleEntryByExaminingPath();
    }
    return result;
  }

  @Nullable
  private String getLibraryGradleEntry(@NotNull AndroidFacet androidFacet) {
    AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
    if (androidModel == null) {
      return null;
    }

    BaseArtifact testArtifact = androidModel.findSelectedTestArtifactInSelectedVariant();

    Library matchedLibrary = null;
    if (testArtifact != null) {
      matchedLibrary = findMatchedLibrary(testArtifact);
    }
    if (matchedLibrary == null) {
      Variant selectedVariant = androidModel.getSelectedVariant();
      matchedLibrary = findMatchedLibrary(selectedVariant.getMainArtifact());
    }
    if (matchedLibrary == null) {
      return null;
    }

    // TODO use getRequestedCoordinates once the interface is fixed.
    MavenCoordinates mavenCoordinates = matchedLibrary.getResolvedCoordinates();
    if (mavenCoordinates == null) {
      return null;
    }
    return mavenCoordinates.getGroupId() +
           GRADLE_PATH_SEPARATOR +
           mavenCoordinates.getArtifactId() +
           GRADLE_PATH_SEPARATOR +
           mavenCoordinates.getVersion();
  }

  @Nullable
  private Library findMatchedLibrary(@NotNull BaseArtifact artifact) {
    for (JavaLibrary library : artifact.getDependencies().getJavaLibraries()) {
      String libraryName = getNameWithoutExtension(library.getJarFile());
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
    VirtualFile[] files = myLibraryEntry.getFiles(OrderRootType.CLASSES);
    if (files.length == 0) {
      return null;
    }
    File file = virtualToIoFile(files[0]);
    String libraryName = myLibraryEntry.getLibraryName();
    if (libraryName == null) {
      return null;
    }

    List<String> pathSegments = splitPath(file.getPath());

    for (int i = 1; i < pathSegments.size() - 2; i++) {
      if (libraryName.startsWith(pathSegments.get(i))) {
        String groupId = pathSegments.get(i - 1);
        String artifactId = pathSegments.get(i);
        String version = pathSegments.get(i + 1);
        if (libraryName.endsWith(version)) {
          return groupId + GRADLE_PATH_SEPARATOR + artifactId + GRADLE_PATH_SEPARATOR + version;
        }
      }
    }
    return null;
  }
}
