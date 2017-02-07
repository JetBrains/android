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

import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author coyote
 */
public class SystemResourceManager extends ResourceManager {
  private final AndroidPlatform myPlatform;
  private final boolean myPublicOnly;

  public SystemResourceManager(@NotNull Project project, @NotNull AndroidPlatform androidPlatform, boolean publicOnly) {
    super(project);
    myPlatform = androidPlatform;
    myPublicOnly = publicOnly;
  }

  @Override
  protected boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    return !myPublicOnly || myPlatform.getSdkData().getTargetData(myPlatform.getTarget()).
      isResourcePublic(type, name);
  }

  @Override
  @NotNull
  public Multimap<String, VirtualFile> getAllResourceDirs() {
    VirtualFile resDir = getResourceDir();
    Multimap<String, VirtualFile> result = HashMultimap.create();
    if (resDir != null) {
      result.put(null, resDir);
    }
    return result;
  }

  @Nullable
  private VirtualFile getResourceDir() {
    String resPath = myPlatform.getTarget().getPath(IAndroidTarget.RESOURCES);
    resPath = FileUtil.toSystemIndependentName(resPath);
    return LocalFileSystem.getInstance().findFileByPath(resPath);
  }

  @Override
  public boolean isResourceDir(@NotNull VirtualFile dir) {
    return dir.equals(getResourceDir());
  }

  @Override
  @NotNull
  public List<VirtualFile> getResourceDirs() {
    String resPath = myPlatform.getTarget().getPath(IAndroidTarget.RESOURCES);
    resPath = FileUtil.toSystemIndependentName(resPath);
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(resPath);
    return dir != null ? Collections.singletonList(dir) : Collections.emptyList();
  }

  @Nullable
  public static SystemResourceManager getInstance(@NotNull ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    return facet != null ? ModuleResourceManagers.getInstance(facet).getSystemResourceManager() : null;
  }

  @Override
  @Nullable
  public synchronized AttributeDefinitions getAttributeDefinitions() {
    return myPlatform.getSdkData().getTargetData(myPlatform.getTarget()).getPublicAttrDefs(myProject);
  }
}
