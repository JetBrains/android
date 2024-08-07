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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** Can plug into the blaze sync system. */
public interface BlazeSyncPlugin {
  ExtensionPointName<BlazeSyncPlugin> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SyncPlugin");

  /**
   * May be used by the plugin to create/edit modules.
   *
   * <p>Using this ensures that the blaze plugin is aware of the modules, won't garbage collect
   * them, and that all module modifications happen in a single transaction.
   */
  interface ModuleEditor {
    /** Creates a new module and registers it with the module editor. */
    Module createModule(String moduleName, ModuleType<?> moduleType);

    /**
     * Edits a module. It will be committed when commit is called.
     *
     * <p>The module will be returned in a cleared state. You should not call this method multiple
     * times.
     */
    ModifiableRootModel editModule(Module module);

    /** Finds a module by name. This doesn't register the module. */
    @Nullable
    Module findModule(String moduleName);

    /** Commits the module editor without garbage collection. */
    void commit();
  }

  /**
   * The {@link WorkspaceType}s supported by this plugin. Not used to choose the project's
   * WorkspaceType.
   */
  default ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of();
  }

  /**
   * @return The default workspace type recommended by this plugin.
   */
  @Nullable
  default WorkspaceType getDefaultWorkspaceType() {
    return null;
  }

  /**
   * @return The module type for the workspace given the workspace type.
   */
  @Nullable
  default ModuleType<?> getWorkspaceModuleType(WorkspaceType workspaceType) {
    return null;
  }

  /**
   * @return The set of supported languages under this workspace type.
   */
  default Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of();
  }

  /** IDs for the additional plugins required to support the given languages. */
  default ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return ImmutableList.of();
  }

  /** Given the rule map, update the sync state for this plugin. Should not have side effects. */
  default void updateSyncState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BlazeVersionData blazeVersionData,
      @Nullable WorkingSet workingSet,
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      SyncMode syncMode) {}

  /**
   * Whether to refresh the execution root directory, which contains files which may have changed
   * during sync, and aren't covered by file watchers.
   *
   * <p>Called prior to updateProjectSdk and updateProjectStructure.
   */
  default boolean refreshExecutionRoot(Project project, BlazeProjectData blazeProjectData) {
    return false;
  }

  /**
   * Initializes any structures the plugin may need to outside of a write action. The API for Python
   * SDK creation needs to be invoked from EDT outside of a write action, and this method will be
   * invoked in that context.
   */
  default void createSdks(Project project, BlazeProjectData blazeProjectData) {}

  /** Updates the sdk for the project. */
  default void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {}

  @Nullable
  default SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    return null;
  }

  /** Modifies the IDE project structure in accordance with the sync data. */
  default void updateProjectStructure(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel) {}

  /**
   * Updates in-memory state that isn't serialized by IntelliJ.
   *
   * <p>Called on sync and on startup, after updateProjectStructure. May not do any write actions.
   */
  default void updateInMemoryState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      SyncMode syncMode) {}

  /** Validates the project. */
  default boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    return true;
  }

  /**
   * Validates the project view.
   *
   * @param project null when called from the project import wizard
   * @return True for success, false for fatal error.
   */
  default boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    return true;
  }

  /** Returns any custom sections that this plugin supports. */
  default Collection<SectionParser> getSections() {
    return ImmutableList.of();
  }

  @Nullable
  default LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    return null;
  }
}
