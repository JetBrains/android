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
package com.android.tools.idea.apk.debugging;

import static com.android.SdkConstants.EXT_JAVA;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.intellij.codeInsight.navigation.NavigationUtil.openFileWithPsiElement;
import static com.intellij.openapi.util.io.FileUtil.join;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DexSourceFiles {
  public static final String SMALI_ROOT_FOLDER_NAME = "smali";
  public static final String SMALI_OUTPUT_FOLDER_NAME = "out";

  @NonNls private static final String SMALI_EXTENSION = "smali";

  @NotNull private final Project myProject;
  @NotNull private final File myOutputFolderPath;

  @NotNull
  public static DexSourceFiles getInstance(@NotNull Project project) {
    return project.getService(DexSourceFiles.class);
  }

  public DexSourceFiles(@NotNull Project project) {
    myProject = project;
    myOutputFolderPath = getDefaultSmaliOutputFolderPath();
  }

  @NotNull
  public File getDefaultSmaliOutputFolderPath() {
    return new File(getBaseDirPath(myProject), join(SMALI_ROOT_FOLDER_NAME, SMALI_OUTPUT_FOLDER_NAME));
  }

  public boolean isJavaFile(@NotNull VirtualFile file) {
    return !file.isDirectory() && EXT_JAVA.equals(file.getExtension());
  }

  public boolean navigateToJavaFile(@NotNull String classFqn) {
    PsiClass javaPsiClass = findJavaPsiClass(classFqn);
    if (javaPsiClass != null) {
      openFileWithPsiElement(javaPsiClass, true, true);
      return true;
    }
    return false;
  }

  @Nullable
  public PsiClass findJavaPsiClass(@NotNull String classFqn) {
    if (DumbService.getInstance(myProject).isDumb()) {
      // Index not ready.
      return null;
    }
    return JavaPsiFacade.getInstance(myProject).findClass(classFqn, GlobalSearchScope.allScope(myProject));
  }

  @NotNull
  public List<String> findJavaClassesIn(@NotNull VirtualFile file) {
    PsiJavaFile psiFile = findPsiJavaFileFor(file);
    if (psiFile != null) {
      PsiClass[] classes = psiFile.getClasses();
      if (classes.length > 0) {
        return Arrays.stream(classes).map(PsiClass::getQualifiedName).collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public String findJavaPackageNameIn(@NotNull VirtualFile file) {
    PsiJavaFile psiFile = findPsiJavaFileFor(file);
    return psiFile != null ? psiFile.getPackageName() : null;
  }

  public boolean isSmaliFile(@NotNull VirtualFile file) {
    return !file.isDirectory() && SMALI_EXTENSION.equals(file.getExtension());
  }

  @Nullable
  private PsiJavaFile findPsiJavaFileFor(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    return psiFile instanceof PsiJavaFile ? (PsiJavaFile)psiFile : null;
  }

  @Nullable
  public VirtualFile findSmaliFile(@NotNull String classFqn) {
    File filePath = findSmaliFilePathForClass(classFqn);
    if (filePath.isFile()) {
      return LocalFileSystem.getInstance().findFileByPath(filePath.getPath());
    }
    return null;
  }

  @NotNull
  public File findSmaliFilePathForClass(@NotNull String classFqn) {
    return new File(myOutputFolderPath, classFqn.replace('.', File.separatorChar) + ".smali");
  }

  @NotNull
  public File findSmaliFilePathForPackage(@NotNull String packageFqn) {
    return new File(myOutputFolderPath, packageFqn.replace('.', File.separatorChar));
  }
}
