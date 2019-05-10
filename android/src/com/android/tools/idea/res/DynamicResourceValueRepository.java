/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.res;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.projectmodel.DynamicResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource repository which contains dynamically registered resource items from the model.
 *
 * <p>The Gradle plugin allows resources to be created on the fly (e.g. you can create a resource called build_time of type string with a
 * value set to a Groovy variable computed at build time). These dynamically created resources are computed at Gradle sync time and provided
 * via the Gradle model.
 *
 * <p>Users expect the resources to "exist" too, when using code completion. The {@link DynamicResourceValueRepository} makes this happen:
 * the repository contents are fetched from the Gradle model rather than by analyzing XML files as is done by the other resource
 * repositories.
 */
public class DynamicResourceValueRepository extends LocalResourceRepository
    implements Disposable, BuildVariantView.BuildVariantSelectionChangeListener, SingleNamespaceResourceRepository {
  private final AndroidFacet myFacet;
  private final ResourceTable myFullTable = new ResourceTable();
  @NotNull private final ResourceNamespace myNamespace;

  private DynamicResourceValueRepository(@NotNull AndroidFacet facet, @NotNull ResourceNamespace namespace) {
    super("Gradle Dynamic");
    myFacet = facet;
    myNamespace = namespace;
    assert facet.requiresAndroidModel();
  }

  private void registerListeners() {
    myFacet.getModule().getProject().getMessageBus().connect(this).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      if (result == ProjectSystemSyncManager.SyncResult.SUCCESS) {
        notifyProjectSynced();
      }
    });

    BuildVariantUpdater.getInstance(myFacet.getModule().getProject()).addSelectionChangeListener(this);
  }

  @Override
  public void dispose() {
    Project project = myFacet.getModule().getProject();
    if (!project.isDisposed()) {
      BuildVariantUpdater.getInstance(project).removeSelectionChangeListener(this);
    }
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet);
  }

  /**
   * Creates a new {@link DynamicResourceValueRepository} for the given {@link AndroidFacet} and registers listeners to keep the repository
   * up to date. The returned repository needs to be registered with a {@link Disposable} parent.
   */
  @NotNull
  public static DynamicResourceValueRepository create(@NotNull AndroidFacet facet, @NotNull ResourceNamespace namespace) {
    DynamicResourceValueRepository repository = new DynamicResourceValueRepository(facet, namespace);
    try {
      repository.registerListeners();
    }
    catch (Throwable t) {
      Disposer.dispose(repository);
      throw t;
    }
    return repository;
  }

  /**
   * Creates a {@link DynamicResourceValueRepository} with the given values.
   */
  @VisibleForTesting
  @NotNull
  public static DynamicResourceValueRepository createForTest(@NotNull AndroidFacet facet,
                                                             @NotNull ResourceNamespace namespace,
                                                             @NotNull Map<String, DynamicResourceValue> values) {
    DynamicResourceValueRepository repository = new DynamicResourceValueRepository(facet, namespace);
    repository.addValues(values);
    Disposer.register(facet, repository);
    return repository;
  }

  @Override
  @NonNull
  protected ResourceTable getFullTable() {
    if (myFullTable.isEmpty()) {
      AndroidModel androidModel = AndroidModel.get(myFacet.getModule());
      if (androidModel == null) {
        return myFullTable;
      }

      addValues(androidModel.getResValues());
    }

    return myFullTable;
  }

  private void notifyProjectSynced() {
    myFullTable.clear(); // compute lazily in getMap
    super.invalidateParentCaches();
  }

  private void addValues(Map<String, DynamicResourceValue> resValues) {
    for (Map.Entry<String, DynamicResourceValue> entry : resValues.entrySet()) {
      DynamicResourceValue field = entry.getValue();
      String name = entry.getKey();

      ResourceType type = field.getType();
      ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, type);
      if (map == null) {
        map = ArrayListMultimap.create();
        myFullTable.put(myNamespace, type, map);
      }
      else if (map.containsKey(name)) {
        // Masked by higher priority source provider
        continue;
      }
      ResourceItem item = new DynamicResourceValueItem(myNamespace, type, name, field.getValue());
      map.put(name, item);
    }
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type, boolean create) {
    if (!namespace.equals(myNamespace)) {
      return create ? ArrayListMultimap.create() : null;
    }

    if (myFullTable.isEmpty()) {
      // Force lazy initialization
      getFullTable();
    }
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  // ---- Implements BuildVariantView.BuildVariantSelectionChangeListener ----

  @Override
  public void selectionChanged() {
    notifyProjectSynced();
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }
}
