/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.resourceManagers;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.ConvertContext;
import java.util.Collection;
import java.util.List;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FrameworkResourceManager extends ResourceManager {
  @NotNull private final Module myModule;
  private final boolean myPublicOnly;

  public FrameworkResourceManager(@NotNull Module module, boolean publicOnly) {
    super(module.getProject());
    myModule = module;
    myPublicOnly = publicOnly;
  }

  @Override
  public boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    AndroidPlatform platform = getPlatform();
    if (platform == null) {
      return false;
    }
    return !myPublicOnly || platform.getSdkData().getTargetData(platform.getTarget()).isResourcePublic(type, name);
  }

  @Nullable
  private VirtualFile getResourceDir() {
    AndroidPlatform platform = getPlatform();
    if (platform == null) {
      return null;
    }
    String resPath = platform.getTarget().getPath(IAndroidTarget.RESOURCES);
    resPath = FileUtil.toSystemIndependentName(resPath);
    return LocalFileSystem.getInstance().findFileByPath(resPath);
  }

  @Override
  public boolean isResourceDir(@NotNull VirtualFile dir) {
    return dir.equals(getResourceDir());
  }

  @Nullable
  public static FrameworkResourceManager getInstance(@NotNull ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    return facet != null ? ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager() : null;
  }

  @Override
  @Nullable
  public AttributeDefinitions getAttributeDefinitions() {
    AndroidPlatform platform = getPlatform();
    if (platform == null) {
      return null;
    }
    return platform.getSdkData().getTargetData(platform.getTarget()).getPublicAttrDefs(myProject);
  }

  @Override
  @NotNull
  protected Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories() {
    ResourceRepository repository = getResourceRepository();
    return repository == null ? ImmutableList.of() : repository.getLeafResourceRepositories();
  }

  @Override
  @NotNull
  protected List<ResourceItem> getResources(
      @NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType, @NotNull String resName) {
    ResourceRepository repository = getResourceRepository();
    return repository == null ? ImmutableList.of() : repository.getResources(namespace, resourceType, resName);
  }

  @Nullable
  private ResourceRepository getResourceRepository() {
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(myModule);
    return repositoryManager == null ? null : repositoryManager.getFrameworkResources(false);
  }

  @Nullable
  private AndroidPlatform getPlatform() {
    return AndroidPlatform.getInstance(myModule);
  }
}
