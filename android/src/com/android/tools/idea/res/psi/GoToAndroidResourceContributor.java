/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.psi;

import static com.android.tools.idea.projectsystem.ModuleSystemUtil.isMainModule;

import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.model.gotosymbol.GoToSymbolProvider;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Exposes Android resources in <a href="https://www.jetbrains.com/help/idea/searching-everywhere.html">search for a target by name</a>.
 */
public class GoToAndroidResourceContributor extends GoToSymbolProvider {
  @Override
  protected void addNames(@NotNull Module module, @NotNull Set<String> result) {
    LocalResourceRepository resources = StudioResourceRepositoryManager.getModuleResources(module);
    if (resources != null) {
      for (SingleNamespaceResourceRepository repository : resources.getLeafResourceRepositories()) {
        repository.accept(item -> {
          result.add(item.getName());
          return ResourceVisitor.VisitResult.CONTINUE;
        });
      }
    }
  }

  @Override
  protected void addItems(@NotNull Module module, @NotNull String name, @NotNull List<NavigationItem> result) {
    LocalResourceRepository resources = StudioResourceRepositoryManager.getModuleResources(module);
    if (resources != null) {
      for (SingleNamespaceResourceRepository repository : resources.getLeafResourceRepositories()) {
        repository.accept(item -> {
          if (item.getName().equals(name)) {
            VirtualFile file = IdeResourcesUtil.getSourceAsVirtualFile(item);
            if (file != null) {
              result.add(new ResourceNavigationItem(item, file, module.getProject()));
            }
          }
          return ResourceVisitor.VisitResult.CONTINUE;
        });
      }
    }
  }

  @Override
  protected boolean acceptModule(@NotNull Module module) {
    return AndroidFacet.getInstance(module) != null && isMainModule(module);
  }
}
