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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceTable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;


public class MockDataResourceRepository extends LocalResourceRepository {
  private final ResourceTable myFullTable;

  protected MockDataResourceRepository(AndroidFacet androidFacet) {
    super("MockData");

    myFullTable = new ResourceTable();

    // Find all the files in the mocks directory
    VirtualFile contentRoot = AndroidRootUtil.getMainContentRoot(androidFacet);
    VirtualFile mocksDir = contentRoot != null ? contentRoot.findFileByRelativePath("/mocks") : null;
    if (mocksDir != null) {
      ImmutableListMultimap.Builder<String, ResourceItem> items = ImmutableListMultimap.builder();
      PsiManager psiManager = PsiManager.getInstance(androidFacet.getModule().getProject());
      ApplicationManager.getApplication().runReadAction(() -> {
        Arrays.stream(mocksDir.getChildren())
          .map(psiManager::findFile)
          .filter(Objects::nonNull)
          .forEach(f -> items.put(f.getName(),
                                  new PsiResourceItem(f.getName(), ResourceType.MOCK, null, null, f)));
      });

      myFullTable.put(null, ResourceType.MOCK, items.build());
    }
  }

  @NonNull
  @Override
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Nullable
  @Override
  protected ListMultimap<String, ResourceItem> getMap(@Nullable String namespace, @NonNull ResourceType type, boolean create) {
    return myFullTable.get(namespace, type);
  }

  @NonNull
  @Override
  public Set<String> getNamespaces() {
    return Collections.emptySet();
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }
}
