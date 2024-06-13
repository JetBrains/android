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

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.projectmodel.DynamicResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.res.LocalResourceRepository;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Resource repository which contains dynamically registered resource items from the model.
 *
 * <p>The Gradle plugin allows resources to be created on the fly (e.g. you can create a resource called build_time of type string with a
 * value set to a Groovy variable computed at build time). These dynamically created resources are computed at Gradle sync time and provided
 * via the Gradle model.
 *
 * <p>Users expect the resources to "exist" too, when using code completion. The {@link DynamicValueResourceRepository} makes this happen:
 * the repository contents are fetched from the Gradle model rather than by analyzing XML files as is done by the other resource
 * repositories.
 */
public class DynamicValueResourceRepository extends LocalResourceRepository<VirtualFile> implements Disposable, SingleNamespaceResourceRepository {
  private final AndroidFacet myFacet;
  @NotNull private final ResourceNamespace myNamespace;
  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @NotNull private final Map<ResourceType, ListMultimap<String, ResourceItem>> myResourceTable = new EnumMap<>(ResourceType.class);

  private DynamicValueResourceRepository(@NotNull AndroidFacet facet,
                                         @NotNull Disposable parentDisposable,
                                         @NotNull ResourceNamespace namespace) {
    super("Gradle Dynamic");
    Disposer.register(parentDisposable, this);
    myFacet = facet;
    myNamespace = namespace;
    assert AndroidModel.isRequired(facet);
  }

  private void registerListeners() {
    myFacet.getModule().getProject().getMessageBus().connect(this).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      if (result.isSuccessful()) {
        notifyProjectSynced();
      }
    });
  }

  @Override
  public void dispose() {}

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet);
  }

  /**
   * Creates a new {@link DynamicValueResourceRepository} for the given {@link AndroidFacet} and registers listeners to keep the repository
   * up to date. The returned repository needs to be registered with a {@link Disposable} parent.
   */
  @NotNull
  public static DynamicValueResourceRepository create(@NotNull AndroidFacet facet,
                                                      @NotNull Disposable parentDisposable,
                                                      @NotNull ResourceNamespace namespace) {
    DynamicValueResourceRepository repository = new DynamicValueResourceRepository(facet, parentDisposable, namespace);
    repository.registerListeners();
    return repository;
  }

  /**
   * Creates a {@link DynamicValueResourceRepository} with the given values.
   */
  @TestOnly
  @NotNull
  public static DynamicValueResourceRepository createForTest(@NotNull AndroidFacet facet,
                                                             @NotNull ResourceNamespace namespace,
                                                             @NotNull Map<String, DynamicResourceValue> values) {
    DynamicValueResourceRepository repository = new DynamicValueResourceRepository(facet, facet, namespace);
    synchronized (ITEM_MAP_LOCK) {
      repository.addValues(values);
    }
    return repository;
  }

  private void notifyProjectSynced() {
    synchronized (ITEM_MAP_LOCK) {
      myResourceTable.clear(); // Computed lazily in getMap.
      invalidateParentCaches(this, ResourceType.values());
    }
  }

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  private void addValues(@NotNull Map<String, DynamicResourceValue> resValues) {
    for (Map.Entry<String, DynamicResourceValue> entry : resValues.entrySet()) {
      DynamicResourceValue field = entry.getValue();
      String name = entry.getKey();

      ResourceType type = field.getType();
      ListMultimap<String, ResourceItem> map = myResourceTable.get(type);
      if (map == null) {
        map = LinkedListMultimap.create();
        myResourceTable.put(type, map);
      }
      else if (map.containsKey(name)) {
        // Masked by higher priority source provider.
        continue;
      }
      ResourceItem item = new DynamicValueResourceItem(this, type, name, field.getValue());
      map.put(name, item);
    }
  }

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    if (!namespace.equals(myNamespace)) {
      return null;
    }

    return getResourceTable().get(type);
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @NotNull
  public ResourceVisitor.VisitResult accept(@NotNull ResourceVisitor visitor) {
    if (visitor.shouldVisitNamespace(myNamespace)) {
      synchronized (ITEM_MAP_LOCK) {
        if (acceptByResources(myResourceTable, visitor) == ResourceVisitor.VisitResult.ABORT) {
          return ResourceVisitor.VisitResult.ABORT;
        }
      }
    }

    return ResourceVisitor.VisitResult.CONTINUE;
  }

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @NotNull
  private Map<ResourceType, ListMultimap<String, ResourceItem>> getResourceTable() {
    if (myResourceTable.isEmpty()) {
      AndroidModel androidModel = AndroidModel.get(myFacet.getModule());
      if (androidModel != null) {
        addValues(androidModel.getResValues());
      }
    }

    return myResourceTable;
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }
}
