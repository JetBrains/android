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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import java.io.File;
import java.util.Collection;
import java.util.Set;
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

    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
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

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData != null) {
      BlazeAndroidSyncData syncData =
          blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
      if (syncData != null) {
        ImmutableCollection<TargetKey> rulesRelatedToContext = null;
        File fileFromContext = null;
        if (contextFile != null) {
          fileFromContext = VfsUtilCore.virtualToIoFile(contextFile);
          rulesRelatedToContext =
              SourceToTargetMap.getInstance(project).getRulesForSourceFile(fileFromContext);
          if (rulesRelatedToContext.isEmpty()) {
            rulesRelatedToContext = null;
          }
        }

        ArtifactLocationDecoder artifactLocationDecoder =
            blazeProjectData.getArtifactLocationDecoder();

        // Sort:
        // - contextFile/res if contextFile is a directory,
        //   to optimize the right click on directory case, or the "closest" string
        //   match to the contextFile from the res directories known to blaze
        // - the rest of the direct dirs, then transitive dirs of the context rules,
        //   then any known res dir in the project
        //   as a backup, in alphabetical order.
        Set<File> resourceDirs = Sets.newTreeSet();
        Set<File> transitiveDirs = Sets.newTreeSet();
        Set<File> allResDirs = Sets.newTreeSet();
        for (AndroidResourceModule androidResourceModule :
            syncData.importResult.androidResourceModules) {

          Collection<File> resources =
              OutputArtifactResolver.resolveAll(
                  project, artifactLocationDecoder, androidResourceModule.resources);

          Collection<File> transitiveResources =
              OutputArtifactResolver.resolveAll(
                  project, artifactLocationDecoder, androidResourceModule.transitiveResources);

          // labelsRelatedToContext should include deps,
          // but as a first pass we only check the rules themselves
          // for resources. If we come up empty, then have anyResDir as a backup.
          allResDirs.addAll(transitiveResources);

          if (rulesRelatedToContext != null
              && !rulesRelatedToContext.contains(androidResourceModule.targetKey)) {
            continue;
          }
          resourceDirs.addAll(resources);
          transitiveDirs.addAll(transitiveResources);
        }
        // No need to show some directories twice.
        transitiveDirs.removeAll(resourceDirs);

        JComboBox resDirCombo = resDirComboAndBrowser.getComboBox();
        // Allow the user to browse and overwrite some of the entries,
        // in case our inference is wrong.
        resDirCombo.setEditable(true);
        // Optimize the right-click on a non-res directory (consider res directory right under that)
        // After the use confirms the choice, a directory will be created if it is missing.
        if (fileFromContext != null && fileFromContext.isDirectory()) {
          File closestDirToContext = new File(fileFromContext.getPath(), "res");
          resDirCombo.setSelectedItem(closestDirToContext);
        } else {
          // If we're not completely sure, let people know there are options
          // via the placeholder text, and put the most likely on top.
          String placeHolder = PLACEHOLDER_TEXT;
          resDirCombo.addItem(placeHolder);
          resDirCombo.setSelectedItem(placeHolder);
          if (fileFromContext != null) {
            File closestDirToContext =
                findClosestDirToContext(fileFromContext.getPath(), resourceDirs);
            closestDirToContext =
                closestDirToContext != null
                    ? closestDirToContext
                    : findClosestDirToContext(fileFromContext.getPath(), transitiveDirs);
            if (closestDirToContext != null) {
              resDirCombo.addItem(closestDirToContext);
              resourceDirs.remove(closestDirToContext);
              transitiveDirs.remove(closestDirToContext);
            }
          }
        }
        if (!resourceDirs.isEmpty() || !transitiveDirs.isEmpty()) {
          for (File resourceDir : resourceDirs) {
            resDirCombo.addItem(resourceDir);
          }
          for (File resourceDir : transitiveDirs) {
            resDirCombo.addItem(resourceDir);
          }
        } else {
          for (File resourceDir : allResDirs) {
            resDirCombo.addItem(resourceDir);
          }
        }
        resDirComboAndBrowser.setVisible(true);
        resDirLabel.setVisible(true);
      }
    }
  }

  private static File findClosestDirToContext(String contextPath, Set<File> resourceDirs) {
    File closestDirToContext = null;
    int curStringDistance = Integer.MAX_VALUE;
    for (File resDir : resourceDirs) {
      int distance = StringUtil.difference(contextPath, resDir.getPath());
      if (distance < curStringDistance) {
        curStringDistance = distance;
        closestDirToContext = resDir;
      }
    }
    return closestDirToContext;
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
