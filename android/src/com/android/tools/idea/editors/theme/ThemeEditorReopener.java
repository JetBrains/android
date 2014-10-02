/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Helper component for tracking opened instances of Theme Editor.
 * Opened instances are tracked by module name, this information is persisted
 * in workspace.xml file and used to re-open all instances after the launch
 * (hence the name)
 */
@State(
  name = "ThemeEditorReopener",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class ThemeEditorReopener extends AbstractProjectComponent implements PersistentStateComponent<ThemeEditorReopener.State> {
  private @NotNull State myState = new State();
  protected ThemeEditorReopener(final Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
    final Set<String> validModules = new HashSet<String>();

    // Go through all initialized AndroidFacets, if there was a Theme Editor
    // opened for a module current facet corresponds to, launch Theme Editor

    // We're iterating through AndroidFacets instead of modules directly because
    // trying to launch Theme Editor on a module which doesn't have AndroidFacet
    // would lead to errors and is incorrect anyway.
    for (final AndroidFacet facet : facets) {
      final Module module = facet.getModule();
      if (isOpenedInModule(module)) {
        validModules.add(module.getName());
        ThemeEditorUtils.openThemeEditor(module);
      }
    }

    // If there is no initialized AndroidFacet for some module for which
    // Theme Editor has been opened, remove it from the set of modules
    final Iterator<String> iterator = myState.moduleNames.iterator();
    while (iterator.hasNext()) {
      String next = iterator.next();
      if (!validModules.contains(next)) {
        iterator.remove();
      }
    }

    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileClosed(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
        if (file instanceof ThemeEditorVirtualFile) {
          final ThemeEditorVirtualFile themeEditorVirtualFile = (ThemeEditorVirtualFile)file;
          myState.moduleNames.remove(themeEditorVirtualFile.getModule().getName());
        }
      }
    });
  }

  private boolean isOpenedInModule(final Module module) {
    return myState.moduleNames.contains(module.getName());
  }

  public void notifyOpened(final Module module) {
    myState.moduleNames.add(module.getName());
  }

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(final State state) {
    this.myState = state;
  }

  public static class State {
    // names of modules for which theme editor was open
    public @NotNull Set<String> moduleNames = new HashSet<String>();
  }
}
