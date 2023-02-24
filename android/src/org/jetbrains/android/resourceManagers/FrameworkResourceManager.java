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

import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.ConvertContext;
import java.nio.file.Path;
import java.util.Collection;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FrameworkResourceManager extends ResourceManager {
  @NotNull private final Module myModule;

  public FrameworkResourceManager(@NotNull Module module) {
    super(module.getProject());
    myModule = module;
  }

  @Nullable
  private VirtualFile getResourceDir() {
    AndroidPlatform platform = getPlatform();
    if (platform == null) {
      return null;
    }
    Path resPath = platform.getTarget().getPath(IAndroidTarget.RESOURCES);
    return LocalFileSystem.getInstance().findFileByNioFile(resPath);
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
    return AndroidTargetData.get(platform.getSdkData(), platform.getTarget()).getPublicAttrDefs(myProject);
  }

  @Override
  @NotNull
  protected Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories() {
    ResourceRepository repository = getResourceRepository();
    return repository == null ? ImmutableList.of() : repository.getLeafResourceRepositories();
  }

  @Nullable
  private ResourceRepository getResourceRepository() {
    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(myModule);
    return repositoryManager == null ? null : repositoryManager.getFrameworkResources(ImmutableSet.of());
  }

  @Nullable
  private AndroidPlatform getPlatform() {
    return AndroidPlatforms.getInstance(myModule);
  }
}
