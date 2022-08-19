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

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.android.tools.idea.smali.psi.SmaliFile;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.codeInsight.navigation.NavigationUtil.openFileWithPsiElement;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SmaliFileNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("apk.smali.file");

  @NotNull private final Project myProject;
  @NotNull private final DexSourceFiles myDexSourceFiles;

  public SmaliFileNotificationProvider(@NotNull Project project) {
    myProject = project;
    myDexSourceFiles = DexSourceFiles.getInstance(project);
  }

  @Override
  @NotNull
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    Module module = ProjectFileIndex.getInstance(myProject).getModuleForFile(file);
    if (module != null && ApkFacet.getInstance(module) != null && myDexSourceFiles.isSmaliFile(file)) {
      File outputFolderPath = myDexSourceFiles.getDefaultSmaliOutputFolderPath();
      File filePath = virtualToIoFile(file);
      if (isAncestor(outputFolderPath, filePath, false)) {
        // The smali file is inside the folder where baksmali generated the smali files by disassembling classes.dex.
        EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
        panel.setText("Disassembled classes.dex file. To set up breakpoints for debugging, please attach Kotlin/Java source files.");

        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
        if (psiFile instanceof SmaliFile) {
          String classFqn = myDexSourceFiles.findJavaClassName((SmaliFile)psiFile);
          if (isNotEmpty(classFqn)) {
            PsiClass javaPsiClass = myDexSourceFiles.findJavaPsiClass(classFqn);
            if (javaPsiClass != null) {
              panel.createActionLabel("Open Kotlin/Java file", () -> openFileWithPsiElement(javaPsiClass, true, true));
            }
            else {
              panel.createActionLabel("Attach Kotlin/Java Sources...", new ChooseAndAttachJavaSourcesTask(classFqn, module, myDexSourceFiles));
            }
          }
        }

        return panel;
      }
    }
    return null;
  }
}
