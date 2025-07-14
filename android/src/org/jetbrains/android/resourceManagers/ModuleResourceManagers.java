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
package org.jetbrains.android.resourceManagers;

import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Objects;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModuleResourceManagers {
  private final Module myModule;

  private FrameworkResourceManager myPublicFrameworkResourceManager;
  private FrameworkResourceManager myFullFrameworkResourceManager;

  private LocalResourceManager myLocalResourceManager;

  @NotNull
  public static ModuleResourceManagers getInstance(@NotNull AndroidFacet facet) {
    //noinspection ConstantConditions (registered in android-plugin.xml, so won't be null
    return facet.getModule().getService(ModuleResourceManagers.class);
  }

  private ModuleResourceManagers(@NotNull Module module) {
    myModule = module;

    MessageBusConnection connection = module.getProject().getMessageBus().connect(module);
    connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      private Sdk myPrevSdk = null;

      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        myLocalResourceManager = null;

        // The system resource managers cache data only from the platform, so they only need to be cleared if the platform changes
        Sdk newSdk = ModuleRootManager.getInstance(module).getSdk();
        if (myPublicFrameworkResourceManager != null || myFullFrameworkResourceManager != null) {
          if (!Objects.equals(myPrevSdk, newSdk)) {
            myPublicFrameworkResourceManager = null;
            myFullFrameworkResourceManager = null;
          }
        }
        myPrevSdk = newSdk;
      }
    });
  }

  @Nullable
  public ResourceManager getResourceManager(@Nullable String resourcePackage) {
    return getResourceManager(resourcePackage, null);
  }

  @Nullable
  public ResourceManager getResourceManager(@Nullable String resourcePackage, @Nullable PsiElement contextElement) {
    if (SYSTEM_RESOURCE_PACKAGE.equals(resourcePackage)) {
      return getFrameworkResourceManager();
    }
    if (contextElement != null && AndroidSdks.getInstance().isInAndroidSdk(contextElement)) {
      return getFrameworkResourceManager();
    }
    return getLocalResourceManager();
  }

  @NotNull
  public LocalResourceManager getLocalResourceManager() {
    if (myLocalResourceManager == null || myLocalResourceManager.getFacet().isDisposed()) {
      myLocalResourceManager = new LocalResourceManager(getFacet());
    }
    return myLocalResourceManager;
  }

  @NotNull
  private AndroidFacet getFacet() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null; // see factory method
    return facet;
  }

  @Nullable
  public FrameworkResourceManager getFrameworkResourceManager() {
    if (myPublicFrameworkResourceManager == null) {
      myPublicFrameworkResourceManager = new FrameworkResourceManager(myModule);
    }
    return myPublicFrameworkResourceManager;
  }
}
