/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.tools.idea.res.SampleDataHelperKt.loadSampleDataItemsAsync;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.res.LocalResourceRepository;
import com.android.tools.res.MultiResourceRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.Atomics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A repository of user-defined sample data. To define new project data sources a new file of folder needs to be created under
 * the {@code sampleData} folder in the project root. The repository provides access to the full contents of the data sources.
 * Selection of items is done by the {@link com.android.ide.common.resources.sampledata.SampleDataManager}
 * <p/>
 * The {@link SampleDataResourceRepository} currently supports three data formats:
 * <ul>
 *   <li><b>Plain text files</b>: Files that allow defining a new item per line
 *   <li><b>JSON files</b>: The SampleDataResourceRepository will extract every possible path that gives access to an array of
 *   elements and provide access to them
 *   <li><b>Directories</b>: Folders that contain a list of images
 * </ul>
 */
final class SampleDataResourceRepository extends LocalResourceRepository<VirtualFile> implements SingleNamespaceResourceRepository, Disposable {
  private static final Logger LOG = Logger.getInstance(SampleDataResourceRepository.class);

  @NotNull private final AndroidFacet myAndroidFacet;
  @NotNull private final ResourceNamespace myNamespace;
  @NotNull private final Map<ResourceType, ListMultimap<String, ResourceItem>> myResourceTable = new EnumMap<>(ResourceType.class);
  private final AtomicReference<CompletableFuture<List<SampleDataResourceItem>>> myUpdateTaskReference = Atomics.newReference(CompletableFuture.completedFuture(ImmutableList.of()));
  private final Executor myUpdateExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("SampleDataResourceRepositoryUpdate", 1);

  public SampleDataResourceRepository(@NotNull AndroidFacet androidFacet, @NotNull Disposable parentDisposable) {
    super("Sample Data");
    Disposer.register(parentDisposable, this);
    myAndroidFacet = androidFacet;

    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(androidFacet);
    myNamespace = repositoryManager.getNamespace();
    loadItems();

    SampleDataListener.getInstance(androidFacet.getModule().getProject());
  }

  @Override
  public void addParent(@NotNull MultiResourceRepository<VirtualFile> parent) {
    if (!myAndroidFacet.isDisposed()) {
      super.addParent(parent);
    }
  }

  /**
   * Reloads user-defined sample data from files on disk. Call this method after the sample data has been updated to reload the contents.
   */
  void reload() {
    loadItems();
  }

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    if (!namespace.equals(myNamespace)) {
      return null;
    }
    return myResourceTable.get(type);
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myAndroidFacet);
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

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void invokeAfterPendingUpdatesFinish(@NotNull Executor executor, @NotNull Runnable callback) {
    myUpdateTaskReference.get().whenComplete((items, exception) -> executor.execute(callback));
  }

  /**
   * Invalidates the current sample data of this repository. Call this method after the sample data has been updated
   * to reload the contents.
   */
  private void loadItems() {
    if (myAndroidFacet.isDisposed()) {
      return;
    }

    CompletableFuture<List<SampleDataResourceItem>> newUpdate = loadSampleDataItemsAsync(myAndroidFacet, this, myUpdateExecutor)
      .whenComplete((items, exception) -> {
        synchronized (ITEM_MAP_LOCK) {
          if (exception != null) {
            LOG.warn(exception);
          }
          if (items == null || exception != null) return;
          myResourceTable.clear();
          if (!items.isEmpty()) {
            HashSet<String> alreadyParsedItems = new HashSet<>();
            ImmutableListMultimap.Builder<String, ResourceItem> mapBuilder = ImmutableListMultimap.builder();
            for (ResourceItem item : items) {
              assert item.getNamespace().equals(myNamespace);
              // Only add to the result if we have not parsed a sample data source with the same name. If there are collisions, we use the
              // dependency order so in, app -> lib, app sample data sources would override the lib ones.
              if (alreadyParsedItems.add(item.getName())) mapBuilder.put(item.getName(), item);
            }
            myResourceTable.put(ResourceType.SAMPLE_DATA, mapBuilder.build());
          }
          setModificationCount(ourModificationCounter.incrementAndGet());
          invalidateParentCaches(this, ResourceType.SAMPLE_DATA);
        }
      });
    // Cancel the previous running task and schedule a new update
    myUpdateTaskReference.getAndSet(newUpdate).cancel(true);
  }
}
