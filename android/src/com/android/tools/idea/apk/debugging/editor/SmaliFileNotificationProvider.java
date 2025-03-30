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
package com.android.tools.idea.apk.debugging.editor;

import static com.intellij.codeInsight.navigation.NavigationUtil.openFileWithPsiElement;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.psi.util.PsiTreeUtil.findChildOfType;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.android.tools.idea.smali.psi.SmaliClassName;
import com.android.tools.idea.smali.psi.SmaliClassSpec;
import com.android.tools.idea.smali.psi.SmaliFile;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import java.io.File;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SmaliFileNotificationProvider implements EditorNotificationProvider {

  @Override
  @Nullable
  public Function<FileEditor, EditorNotificationPanel> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(file);
    if (module == null) return null;
    DexSourceFiles dexSourceFiles = DexSourceFiles.getInstance(project);
    if (ApkFacet.getInstance(module) == null || !dexSourceFiles.isSmaliFile(file)) return null;
    File outputFolderPath = dexSourceFiles.getDefaultSmaliOutputFolderPath();
    File filePath = virtualToIoFile(file);
    if (!isAncestor(outputFolderPath, filePath, false)) return null;
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    String classFqn = (psiFile instanceof SmaliFile) ? findJavaClassName((SmaliFile)psiFile) : null;
    PsiClass javaPsiClass = (isNotEmpty(classFqn)) ? dexSourceFiles.findJavaPsiClass(classFqn) : null;
    PsiAnchor psiClassAnchor = javaPsiClass != null ? PsiAnchor.create(javaPsiClass) : null;
    // The smali file is inside the folder where baksmali generated the smali files by disassembling classes.dex.
    return (fileEditor) -> createPanel(module, fileEditor, psiClassAnchor, classFqn);
  }

  @NotNull
  private static EditorNotificationPanel createPanel(@NotNull Module module,
                                                     @NotNull FileEditor fileEditor,
                                                     @Nullable PsiAnchor psiClassAnchor,
                                                     @Nullable String classFqn) {
    EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
    panel.setText("Disassembled classes.dex file. To set up breakpoints for debugging, please attach Kotlin/Java source files.");
    if (isNotEmpty(classFqn)) {
      if (psiClassAnchor != null) {
        panel.createActionLabel("Open Kotlin/Java file", () -> {
          PsiElement javaPsiClass = psiClassAnchor.retrieve();
          if (javaPsiClass == null) return;
          openFileWithPsiElement(javaPsiClass, true, true);
        });
      }
      else {
        panel.createActionLabel("Attach Kotlin/Java Sources...",
                                new ChooseAndAttachJavaSourcesTask(classFqn, module, DexSourceFiles.getInstance(module.getProject())));
      }
    }
    return panel;
  }

  @Nullable
  public static String findJavaClassName(@NotNull SmaliFile smaliFile) {
    SmaliClassSpec classSpec = findChildOfType(smaliFile, SmaliClassSpec.class);
    if (classSpec != null) {
      SmaliClassName className = classSpec.getClassName();
      return className != null ? className.getJavaClassName() : null;
    }
    return null;
  }
}
