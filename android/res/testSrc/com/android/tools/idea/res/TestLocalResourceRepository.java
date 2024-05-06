/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.res.LocalResourceRepository;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestLocalResourceRepository extends LocalResourceRepository<VirtualFile> implements SingleNamespaceResourceRepository {
  @NotNull private final ResourceNamespace myNamespace;
  @NotNull private final ResourceTable myResourceTable = new ResourceTable();

  public TestLocalResourceRepository(@NotNull ResourceNamespace namespace) {
    super("unit test");
    myNamespace = namespace;
  }

  @SuppressWarnings("InstanceGuardedByStatic")
  @GuardedBy("ITEM_MAP_LOCK")
  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    return myResourceTable.get(namespace, type);
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public String getPackageName() {
    return myNamespace.getPackageName();
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return Collections.emptySet();
  }

  @Override
  @NotNull
  public ResourceVisitor.VisitResult accept(@NotNull ResourceVisitor visitor) {
    for (Map.Entry<ResourceNamespace, Map<ResourceType, ListMultimap<String, ResourceItem>>> entry : myResourceTable.rowMap().entrySet()) {
      if (visitor.shouldVisitNamespace(entry.getKey())) {
        if (acceptByResources(entry.getValue(), visitor) == ResourceVisitor.VisitResult.ABORT) {
          return ResourceVisitor.VisitResult.ABORT;
        }
      }
    }

    return ResourceVisitor.VisitResult.CONTINUE;
  }

  public void addResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType,
                           @NotNull ListMultimap<String, ResourceItem> resources) {
    myResourceTable.put(namespace, resourceType, resources);
  }
}
