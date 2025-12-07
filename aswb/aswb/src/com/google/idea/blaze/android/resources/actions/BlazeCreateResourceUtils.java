/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.resources.actions;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import java.io.File;
import javax.swing.JComboBox;
import org.jetbrains.annotations.Nullable;

/** Utilities for setting up create resource actions and dialogs. */
class BlazeCreateResourceUtils {

  private static final String PLACEHOLDER_TEXT =
      "choose a res/ directory with dropdown or browse button";

  // TODO(b/295880481): Revisit how resources are created. This file conflates logic and UI, but it
  // also reveals that the integration with Studio is done at the wrong level.
  static void setupResDirectoryChoices(
      Project project,
      @Nullable VirtualFile contextFile,
      JBLabel resDirLabel,
      ComboboxWithBrowseButton resDirComboAndBrowser) {
    // Reset the item list before filling it back up.
    resDirComboAndBrowser.getComboBox().removeAllItems();

    if (contextFile == null) {
      return;
    }
    // For query sync, populates the combo box with either the directory itself, if a directory
    // was right-clicked, or the containing directory, if a file was right-clicked. This
    // could be augmented with additional options (i.e. res folders in higher directories and
    // perhaps other res folders)
    File fileFromContext = VfsUtilCore.virtualToIoFile(contextFile);
    if (!fileFromContext.isDirectory()) {
      fileFromContext = fileFromContext.getParentFile();
    }
    File closestDirToContext = new File(fileFromContext.getPath(), "res");

    @SuppressWarnings({"unchecked", "rawtypes"}) // Class comes with raw type from JetBrains
    JComboBox resDirCombo = resDirComboAndBrowser.getComboBox();
    resDirCombo.setEditable(true);
    resDirCombo.addItem(closestDirToContext);
    resDirCombo.setSelectedItem(closestDirToContext);
    resDirComboAndBrowser.setVisible(true);
    resDirLabel.setVisible(true);
    return;
  }

  static PsiDirectory getResDirFromUI(Project project, ComboboxWithBrowseButton directoryCombo) {
    PsiManager psiManager = PsiManager.getInstance(project);
    Object selectedItem = directoryCombo.getComboBox().getEditor().getItem();
    File selectedFile = null;
    if (selectedItem instanceof File) {
      selectedFile = (File) selectedItem;
    } else if (selectedItem instanceof String) {
      String selectedDir = (String) selectedItem;
      if (!selectedDir.equals(PLACEHOLDER_TEXT)) {
        selectedFile = new File(selectedDir);
      }
    }
    if (selectedFile == null) {
      return null;
    }
    final File finalSelectedFile = selectedFile;
    return ApplicationManager.getApplication()
        .runWriteAction(
            new Computable<PsiDirectory>() {
              @Override
              public PsiDirectory compute() {
                return DirectoryUtil.mkdirs(psiManager, finalSelectedFile.getPath());
              }
            });
  }

  static VirtualFile getResDirFromDataContext(VirtualFile contextFile) {
    // Check if the contextFile is somewhere in
    // the <path>/res/resType/foo.xml hierarchy and return <path>/res/.
    if (contextFile.isDirectory()) {
      if (contextFile.getName().equalsIgnoreCase(SdkConstants.FD_RES)) {
        return contextFile;
      }
      if (ResourceFolderType.getFolderType(contextFile.getName()) != null) {
        VirtualFile parent = contextFile.getParent();
        if (parent != null && parent.getName().equalsIgnoreCase(SdkConstants.FD_RES)) {
          return parent;
        }
      }
    } else {
      VirtualFile parent = contextFile.getParent();
      if (parent != null && ResourceFolderType.getFolderType(parent.getName()) != null) {
        // Otherwise, the contextFile is a file w/ a parent that is plausible.
        // Recurse one level, on the parent.
        return getResDirFromDataContext(parent);
      }
    }
    // Otherwise, it may be too ambiguous to figure out (e.g., we're in a .java file).
    return null;
  }
}
