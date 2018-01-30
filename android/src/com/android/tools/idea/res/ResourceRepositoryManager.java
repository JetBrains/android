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
import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResourceRepositoryManager implements Disposable {
  private static final Key<ResourceRepositoryManager> KEY = Key.create(ResourceRepositoryManager.class.getName());

  private static final Object APP_RESOURCES_LOCK = new Object();
  private static final Object PROJECT_RESOURCES_LOCK = new Object();
  private static final Object MODULE_RESOURCES_LOCK = new Object();

  private final AndroidFacet myFacet;
  private final CachedValue<ResourceNamespace> myNamespace;

  @GuardedBy("APP_RESOURCES_LOCK")
  private AppResourceRepository myAppResources;

  @GuardedBy("PROJECT_RESOURCES_LOCK")
  private ProjectResourceRepository myProjectResources;

  @GuardedBy("MODULE_RESOURCES_LOCK")
  private LocalResourceRepository myModuleResources;

  @NotNull
  public static ResourceRepositoryManager getOrCreateInstance(@NotNull AndroidFacet facet) {
    ResourceRepositoryManager instance = facet.getUserData(KEY);
    if (instance == null) {
      instance = facet.putUserDataIfAbsent(KEY, new ResourceRepositoryManager(facet));
    }
    return instance;
  }

  private ResourceRepositoryManager(@NotNull AndroidFacet facet) {
    myFacet = facet;
    Disposer.register(facet, this);

    myNamespace = CachedValuesManager.getManager(facet.getModule().getProject()).createCachedValue(() -> {
      // TODO(namespaces): read the merged manifest.
      Manifest manifest = myFacet.getManifest();
      if (manifest != null) {
        String packageName = manifest.getPackage().getValue();
        if (!StringUtil.isEmptyOrSpaces(packageName)) {
          ResourceNamespace namespace = ResourceNamespace.fromPackageName(packageName);
          // Provide the PSI element as a dependency, so we recompute on every change to the manifest.
          return CachedValueProvider.Result.create(namespace, manifest.getXmlTag());
        }
      }
      return null;
    }, false);
  }

  @Contract("true -> !null")
  @Nullable
  AppResourceRepository getAppResources(boolean createIfNecessary) {
    return ApplicationManager.getApplication().runReadAction((Computable<AppResourceRepository>)() -> {
      synchronized (APP_RESOURCES_LOCK) {
        if (myAppResources == null && createIfNecessary) {
          myAppResources = AppResourceRepository.create(myFacet);
          Disposer.register(this, myAppResources);
        }
        return myAppResources;
      }
    });
  }

  @Contract("true -> !null")
  @Nullable
  ProjectResourceRepository getProjectResources(boolean createIfNecessary) {
    return ApplicationManager.getApplication().runReadAction((Computable<ProjectResourceRepository>)() -> {
      synchronized (PROJECT_RESOURCES_LOCK) {
        if (myProjectResources == null && createIfNecessary) {
          myProjectResources = ProjectResourceRepository.create(myFacet);
          Disposer.register(this, myProjectResources);
        }
        return myProjectResources;
      }
    });
  }

  @Contract("true -> !null")
  @Nullable
  public LocalResourceRepository getModuleResources(boolean createIfNecessary) {
    return ApplicationManager.getApplication().runReadAction((Computable<LocalResourceRepository>)() -> {
      synchronized (MODULE_RESOURCES_LOCK) {
        if (myModuleResources == null && createIfNecessary) {
          myModuleResources = ModuleResourceRepository.create(myFacet);
          Disposer.register(this, myModuleResources);
        }
        return myModuleResources;
      }
    });
  }

  public void refreshResources() {
    synchronized (MODULE_RESOURCES_LOCK) {
      if (myModuleResources != null) {
        Disposer.dispose(myModuleResources);
        myModuleResources = null;
      }
    }

    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myProjectResources != null) {
        Disposer.dispose(myProjectResources);
        myProjectResources = null;
      }
    }

    synchronized (APP_RESOURCES_LOCK) {
      if (myAppResources != null) {
        Disposer.dispose(myAppResources);
        myAppResources = null;
      }
    }
  }

  @Override
  public void dispose() {
    // There's nothing to dispose in this object, but the actual resource repositories may need to do clean-up and they are children
    // of this object in the Disposer hierarchy.
  }

  public void resetAllCaches() {
    refreshResources();
    ConfigurationManager.getOrCreateInstance(myFacet.getModule()).getResolverCache().reset();
    ResourceFolderRegistry.reset();
    FileResourceRepository.reset();
  }

  @NotNull
  public AaptOptions.Namespacing getNamespacing() {
    AndroidModel model = myFacet.getConfiguration().getModel();
    if (model != null) {
      return model.getNamespacing();
    } else {
      return AaptOptions.Namespacing.DISABLED;
    }
  }

  /**
   * Returns the {@link ResourceNamespace} used by the current module.
   *
   * <p>This is read from the manifest, so needs to be run inside a read action.
   */
  @NotNull
  public ResourceNamespace getNamespace() {
    if (getNamespacing() == AaptOptions.Namespacing.DISABLED) {
      return ResourceNamespace.RES_AUTO;
    }

    return ObjectUtils.notNull(myNamespace.getValue(), ResourceNamespace.RES_AUTO);
  }
}
