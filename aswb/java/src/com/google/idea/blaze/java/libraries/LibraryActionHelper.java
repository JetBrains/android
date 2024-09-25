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
package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Helper methods for converting between IntelliJ and Blaze libraries. */
public final class LibraryActionHelper {
  private LibraryActionHelper() {}

  /**
   * Returns the first {@link LibraryOrderEntry} containing this file, or null if none are found.
   */
  @Nullable
  public static LibraryOrderEntry findLibraryForFile(VirtualFile vf, @NotNull Project project) {
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    return index.getOrderEntriesForFile(vf).stream()
        .filter(e -> e instanceof LibraryOrderEntry)
        .map(e -> (LibraryOrderEntry) e)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  public static BlazeJarLibrary findLibraryFromIntellijLibrary(Project project, Library library) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return projectData != null
        ? findLibraryFromIntellijLibrary(project, projectData, library)
        : null;
  }

  @Nullable
  public static BlazeJarLibrary findLibraryFromIntellijLibrary(
      Project project, BlazeProjectData blazeProjectData, Library library) {
    String libName = library.getName();
    if (libName == null) {
      return null;
    }
    LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(libName);
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    if (syncData == null) {
      Messages.showErrorDialog(project, "Project isn't synced. Please resync project.", "Error");
      return null;
    }
    return syncData.getImportResult().libraries.get(libraryKey);
  }

  @Nullable
  static BlazeJarLibrary findBlazeLibraryForAction(Project project, AnActionEvent e) {
    Library library = findLibraryForAction(e);
    if (library == null) {
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return projectData != null
        ? findLibraryFromIntellijLibrary(project, projectData, library)
        : null;
  }

  @Nullable
  static Library findLibraryForAction(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      NamedLibraryElementNode node = findLibraryNode(e.getDataContext());
      if (node != null) {
        String libraryName = node.getName();
        if (StringUtil.isNotEmpty(libraryName)) {
          LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
          return libraryTable.getLibraryByName(libraryName);
        }
      }
    }
    return null;
  }

  @Nullable
  private static NamedLibraryElementNode findLibraryNode(DataContext dataContext) {
    Navigatable[] navigatables = CommonDataKeys.NAVIGATABLE_ARRAY.getData(dataContext);
    if (navigatables != null && navigatables.length == 1) {
      Navigatable navigatable = navigatables[0];
      if (navigatable instanceof NamedLibraryElementNode) {
        return (NamedLibraryElementNode) navigatable;
      }
    }
    return null;
  }
}
