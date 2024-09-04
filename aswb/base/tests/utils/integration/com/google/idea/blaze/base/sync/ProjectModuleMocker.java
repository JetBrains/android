/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.testing.ServiceHelper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Disposer;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Mocks out module editing, committing changes, and finding project modules during sync integration
 * tests.
 */
class ProjectModuleMocker implements Disposable {

  private Map<String, ModifiableRootModel> modules = Maps.newHashMap();
  private ImmutableList<ContentEntry> workspaceContentEntries = ImmutableList.of();

  ProjectModuleMocker(Project project, Disposable parentDisposable) {
    ServiceHelper.registerApplicationService(
        ModuleEditorProvider.class, MockModuleEditor::new, parentDisposable);
    ServiceHelper.registerProjectService(
        project, ModuleFinder.class, new MockModuleFinder(), parentDisposable);
    Disposer.register(parentDisposable, this);
  }

  /** The workspace content entries created during sync */
  ImmutableList<ContentEntry> getWorkspaceContentEntries() {
    return workspaceContentEntries;
  }

  /** The modules created during sync */
  private Module getModuleCreatedDuringSync(String module) {
    ModifiableRootModel modifiableRootModel = modules.get(module);
    return modifiableRootModel != null ? modifiableRootModel.getModule() : null;
  }

  @Override
  public void dispose() {
    // do nothing. Modules actually being disposed will be registed as children of this disposable
  }

  // Since MockModuleEditor does not actually commit modules, the normal ModuleManager
  // won't find modules we've created. This helps look up modules for later stages of Sync.
  // We could override ModuleManager, but that has a wide interface and there are a lot of
  // changes across API versions.
  private class MockModuleFinder implements ModuleFinder {

    MockModuleFinder() {}

    @Nullable
    @Override
    public Module findModuleByName(String name) {
      return getModuleCreatedDuringSync(name);
    }
  }

  private class MockModuleEditor extends ModuleEditorImpl {
    MockModuleEditor(Project project, BlazeImportSettings importSettings) {
      super(project, importSettings);
    }

    @Override
    public void commit() {
      // don't commit module changes,
      // and make sure they're properly disposed when the test is finished
      for (ModifiableRootModel model : modules.values()) {
        Disposer.register(ProjectModuleMocker.this, model.getModule());
        if (model.getModule().getName().equals(BlazeDataStorage.WORKSPACE_MODULE_NAME)) {
          workspaceContentEntries = ImmutableList.copyOf(model.getContentEntries());
        }
      }
      ProjectModuleMocker.this.modules = modules;
    }
  }
}
