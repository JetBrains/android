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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class TestLocalResourceRepository extends LocalResourceRepository {
  private final ResourceTable myResourceTable = new ResourceTable();

  public TestLocalResourceRepository() {
    super("unit test");
  }

  @Override
  @NonNull
  public ResourceTable getFullTable() {
    return myResourceTable;
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NonNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myResourceTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myResourceTable.put(namespace, type, multimap);
    }

    return multimap;
  }

  @Override
  @NotNull
  public Set<ResourceNamespace> getNamespaces() {
    return myResourceTable.rowKeySet();
  }

  @Override
  public void getLeafResourceRepositories(@NonNull Collection<SingleNamespaceResourceRepository> result) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return Collections.emptySet();
  }
}
