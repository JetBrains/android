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
package com.android.tools.idea.gradle.service.sync;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.service.sync.change.ProjectStructureChange;
import com.android.tools.idea.gradle.service.sync.service.Ide2GradleModuleDependencyService;
import com.android.tools.idea.gradle.service.sync.service.Ide2GradleProjectSyncService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class facades functionality of listening ide project structure change events and propagating them to the underlying gradle
 * config (if possible).
 */
public class Ide2GradleProjectSyncFacade implements StartupActivity {

  private static final Logger LOG = Logger.getInstance(Ide2GradleProjectSyncFacade.class);

  @NotNull private static final Map<Key<?>, Ide2GradleProjectSyncService<?>> ourSyncServices = Maps.newHashMap();
  static {
    // This might be re-written to extension point to provide true pluggability.
    Ide2GradleProjectSyncService[] services = { new Ide2GradleModuleDependencyService() };
    for (Ide2GradleProjectSyncService service : services) {
      ourSyncServices.put(service.getKey(), service);
    }
  }

  /** States for a value which is assumed to be ignored. */
  @NotNull private static final String DUMMY_VALUE = "___DUMMY___";

  @NotNull private final AtomicReference<DataNode<ProjectData>> myLastSeenIdeProject = new AtomicReference<DataNode<ProjectData>>();
  @NotNull private final SyncEntityDataComparisonStrategy       myEqualityStrategy   = new SyncEntityDataComparisonStrategy();

  @Override
  public void runActivity(@NotNull final Project project) {
    Runnable task = new Runnable() {
      @Override
      public void run() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Following services are used for ide -> gradle changes propagation: " + ourSyncServices);
        }
        myLastSeenIdeProject.set(buildIdeProjectState(project));
        project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
          @Override
          public void rootsChanged(ModuleRootEvent event) {
            // We don't want to trigger 'ide -> gradle' processing for ide project structure changes triggered by the gradle integration.
            GradleSyncState syncState = GradleSyncState.getInstance(project);
            if (!syncState.isSyncInProgress()) {
              // 'Project modification' event is sent when PSI-document state is inconsistent, that's why we postpone the processing
              // until the modification is actually finished.
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  propagateIdeChangesIfPossible(project);
                }
              });
            }
          }
        });
      }
    };
    if (project.isInitialized()) {
      task.run();
    }
    else {
      StartupManager.getInstance(project).registerPostStartupActivity(task);
    }
  }

  @VisibleForTesting
  public void testCheckChanges(@NotNull Project ideProject) {
    if (myLastSeenIdeProject.get() == null) {
      myLastSeenIdeProject.set(buildIdeProjectState(ideProject));
    }
    else {
      propagateIdeChangesIfPossible(ideProject);
    }
  }

  /**
   * Asks to check if there are any ide project structure since the previous ide project state and try to propagate
   * them to external system config (if possible).
   *
   * @param ideProject  current ide project
   */
  private void propagateIdeChangesIfPossible(@NotNull final Project ideProject) {
    // The ide doesn't provide any information about project structure change details, i.e. it just says 'something is changed'.
    // That's why we keep previous project state and build project structure diff whenever that 'something is changed' event is fired.

    DataNode<ProjectData> previousIdeProjectState = myLastSeenIdeProject.get();
    if (previousIdeProjectState == null) {
      assert false;
      return;
    }
    DataNode<ProjectData> currentIdeProjectState = buildIdeProjectState(ideProject);
    myLastSeenIdeProject.set(currentIdeProjectState);
    final Collection<ProjectStructureChange> changes = Lists.newArrayList();
    fillSyncInput(Collections.<DataNode<?>>singletonList(previousIdeProjectState),
                  Collections.<DataNode<?>>singletonList(currentIdeProjectState),
                  ideProject,
                  changes);
    if (changes.isEmpty()) {
      return;
    }
    GradleSyncState.getInstance(ideProject).runSyncTransparentAction(new Runnable() {
      @Override
      public void run() {
        for (ProjectStructureChange change : changes) {
          Ide2GradleProjectSyncService<?> service = ourSyncServices.get(change.getKey());
          if (service == null) {
            if (LOG.isDebugEnabled()) {
              LOG.debug(String.format("Skipping change '%s' as no service is registered for it's key (%s)", change, change.getKey()));
            }
            continue;
          }
          //noinspection unchecked
          boolean ok = service.flush(change, ideProject);
          if (!ok && LOG.isDebugEnabled()) {
            LOG.debug(String.format("Can't flush IDE project structure change '%s' via sync service %s", change, service));
          }
        }
      }
    });
  }

  @NotNull
  private static DataNode<ProjectData> buildIdeProjectState(@NotNull Project project) {
    ProjectData projectData = new ProjectData(ProjectSystemId.IDE, project.getName(), DUMMY_VALUE, DUMMY_VALUE);
    DataNode<ProjectData> result = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!GradleConstants.SYSTEM_ID.getId().equals(module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID))) {
        continue;
      }
      String linkedProjectPath = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH);
      if (Strings.isNullOrEmpty(linkedProjectPath)) {
        continue;
      }
      buildModuleState(module, linkedProjectPath, result);
    }
    return result;
  }

  private static void buildModuleState(@NotNull Module module,
                                       @NotNull String linkedExternalProjectPath,
                                       @NotNull DataNode<ProjectData> parent)
  {
    // Note that we build only module dependencies here as our goal is to setup common infrastructure for syncing ide project changes
    // into gradle configs and it's triggered by 'handle module dependency added at the ide side request (issue-73938). So, we want
    // to create that common infrastructure with implementation only for module dependencies, review it and expand later if
    // everything is ok.

    DataNode<ModuleData> result = new DataNode<ModuleData>(ProjectKeys.MODULE, buildModuleData(module, linkedExternalProjectPath), parent);
    OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry entry : entries) {
      if (!(entry instanceof ModuleOrderEntry)) {
        continue;
      }
      ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
      Module dependencyModule = moduleOrderEntry.getModule();
      if (dependencyModule == null) {
        continue;
      }
      String externalSystemId = dependencyModule.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID);
      if (externalSystemId != null && !GradleConstants.SYSTEM_ID.getId().equals(externalSystemId)) {
        // Skip ide modules mapped to another external system.
        continue;
      }
      buildModuleDependencyState(moduleOrderEntry, result);
    }
    parent.addChild(result);
  }

  @NotNull
  private static ModuleData buildModuleData(@NotNull Module module, @Nullable String linkedExternalProjectPath) {
    String linkedExternalProjectPathToUse = linkedExternalProjectPath;
    if (linkedExternalProjectPathToUse == null) {
      linkedExternalProjectPathToUse = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH);
    }
    if (linkedExternalProjectPathToUse == null) {
      linkedExternalProjectPathToUse = DUMMY_VALUE;
    }
    return new ModuleData(DUMMY_VALUE, ProjectSystemId.IDE, DUMMY_VALUE, module.getName(), DUMMY_VALUE, linkedExternalProjectPathToUse);
  }

  private static void buildModuleDependencyState(@NotNull ModuleOrderEntry dependency, @NotNull DataNode<ModuleData> parent) {
    Module dependencyModule = dependency.getModule();
    if (dependencyModule == null) {
      assert false;
      return;
    }
    ModuleDependencyData data =
      new ModuleDependencyData(buildModuleData(dependency.getOwnerModule(), null), buildModuleData(dependencyModule, null));
    DataNode<ModuleDependencyData> node = new DataNode<ModuleDependencyData>(ProjectKeys.MODULE_DEPENDENCY, data, parent);
    parent.addChild(node);
  }

  private void fillSyncInput(@NotNull Iterable<DataNode<?>> previousStateNodes, @NotNull Iterable<DataNode<?>> currentStateNodes,
                             @NotNull Project project,
                             @NotNull Collection<ProjectStructureChange> changes)
  {
    Collection<DataNode<?>> previousStateNodesToUse = Lists.newArrayList(previousStateNodes);
    Collection<DataNode<?>> currentStateNodesToUse = Lists.newArrayList(currentStateNodes);
    for (DataNode<?> previousStateNode : previousStateNodes) {
      DataNode<?> matchingCurrentStateNode = find(previousStateNode, currentStateNodesToUse, project);
      registerChangeIfAppropriate(previousStateNode, matchingCurrentStateNode, project, changes);
      if (matchingCurrentStateNode != null) {
        currentStateNodesToUse.remove(matchingCurrentStateNode);
        fillSyncInput(previousStateNode.getChildren(), matchingCurrentStateNode.getChildren(), project, changes);
      }
      previousStateNodesToUse.remove(previousStateNode);
    }
    for (DataNode<?> notMatchedCurrentStateNode : currentStateNodesToUse) {
      registerChangeIfAppropriate(null, notMatchedCurrentStateNode, project, changes);
    }
  }

  @SuppressWarnings("unchecked")
  private static void registerChangeIfAppropriate(@Nullable DataNode previousStateNode,
                                                  @Nullable DataNode currentStateNode,
                                                  @NotNull Project project,
                                                  @NotNull Collection<ProjectStructureChange> changes)
  {
    if (previousStateNode == null && currentStateNode == null) {
      throw new IllegalArgumentException("Can't register project structure change. Reason: both given 'previous' and 'current' state "
                                         + "nodes are undefined. Changes registered so far: " + changes);
    }
    Key<?> key = previousStateNode == null ? null : previousStateNode.getKey();
    if (key == null) {
      key = currentStateNode.getKey();
    }

    Ide2GradleProjectSyncService<?> service = ourSyncServices.get(key);
    if (service != null) {
      ProjectStructureChange<?> change = service.build(previousStateNode, currentStateNode, project);
      if (change != null) {
        changes.add(change);
      }
    }
    if (currentStateNode == null) {
      for (DataNode childNode : (Collection<DataNode>)previousStateNode.getChildren()) {
        registerChangeIfAppropriate(childNode, null, project, changes);
      }
    }
    else if (previousStateNode == null) {
      for (DataNode childNode : (Collection<DataNode>)currentStateNode.getChildren()) {
        registerChangeIfAppropriate(null, childNode, project, changes);
      }
    }
  }

  /**
   * Tries to map given node to a given collection of candidates. The thing is that we want to match two nodes which point
   * to the same entity but have different configuration. E.g. a node which points to particular library dependency with
   * <code>'exported'</code> set to <code>'true'</code> should match to a node which points to the same dependency with
   * <code>'exported'</code> flag set to <code>'false'</code>.
   *
   * @param node            a node to match to one of the given candidates
   * @param candidateNodes  candidate nodes to search for a match of the given node
   * @param project         current ide project
   * @return                one of candidate nodes matched to the given 'base' node (if found);
   *                        <code>null</code> otherwise
   */
  @Nullable
  private DataNode<?> find(@NotNull DataNode<?> node, @NotNull Iterable<DataNode<?>> candidateNodes, @NotNull Project project) {
    for (DataNode<?> candidateNode : candidateNodes) {
      if (!node.getKey().equals(candidateNode.getKey())) {
        continue;
      }
      if (myEqualityStrategy.isSameNode(candidateNode, node, project)) {
        return candidateNode;
      }
    }
    return null;
  }
}
