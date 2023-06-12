/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug;

import com.android.SdkConstants;
import com.android.annotations.concurrency.GuardedBy;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.diagnostics.heap.HeapSnapshotTraverseService;
import com.android.tools.sdk.AndroidSdkData;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.StudioAndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectResourceIdResolver implements ResourceIdResolver {
  @NotNull
  private static final List<String> myPublicFileNames = ImmutableList.of("public.xml", "public-final.xml", "public-staging.xml");

  @NotNull
  private static final Object myPublicResourceIdMapLock = new Object();

  @GuardedBy("myPublicResourceIdMapLock")
  @NotNull
  private static final Map<IAndroidTarget, Int2ObjectMap<String>> myPublicResourceIdMap = new WeakHashMap<>();

  @NotNull
  private static final Logger LOG = Logger.getInstance(ProjectResourceIdResolver.class);

  private final Project myProject;

  private Int2ObjectMap<String> myIdMap;
  private boolean myInitialized;

  @NotNull
  public static ResourceIdResolver getInstance(@NotNull Project project) {
    return project.getService(ResourceIdResolver.class);
  }

  private ProjectResourceIdResolver(@NotNull Project project) {
    myProject = project;
  }

  /** Returns the resource name corresponding to a given id if the id is present in the Android framework's exported ids (in public.xml) */
  @Override
  @Nullable
  public String getAndroidResourceName(int resId) {
    if (!myInitialized) {
      myIdMap = getIdMap();
      myInitialized = true;
    }

    return myIdMap == null ? null : myIdMap.get(resId);
  }

  @NotNull
  private static Int2ObjectMap<String> buildPublicResourceIdMap(@NotNull IAndroidTarget target) {
    Path resDirPath = target.getPath(IAndroidTarget.RESOURCES);

    Int2ObjectMap<String> resourceIdMap = new Int2ObjectOpenHashMap<>();

    for (String fileName : myPublicFileNames) {
      Path publicXmlPath = resDirPath.resolve(SdkConstants.FD_RES_VALUES).resolve(fileName);
      VirtualFile publicXml = LocalFileSystem.getInstance().findFileByNioFile(publicXmlPath);

      if (publicXml != null) {
        try {
          MyPublicResourceIdMapBuilder builder = new MyPublicResourceIdMapBuilder();
          NanoXmlUtil.parse(publicXml.getInputStream(), builder);

          builder.getIdMap().forEachEntry((key, value) -> {
            resourceIdMap.put(key, value);
            return true;
          });
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return resourceIdMap;
  }

  @Nullable
  private static Int2ObjectMap<String> getPublicIdMap(@NotNull IAndroidTarget target) {
    synchronized (myPublicResourceIdMapLock) {
      return myPublicResourceIdMap.computeIfAbsent(target, ProjectResourceIdResolver::buildPublicResourceIdMap);
    }
  }

  private Int2ObjectMap<String> getIdMap() {
    AndroidFacet facet = null;
    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      facet = AndroidFacet.getInstance(m);
      if (facet != null) {
        break;
      }
    }

    AndroidSdkData sdkData = facet == null ? null : StudioAndroidSdkData.getSdkData(facet);
    if (sdkData == null) {
      return null;
    }

    IAndroidTarget[] targets = sdkData.getTargets();
    if (targets.length == 0) {
      return null;
    }

    return getPublicIdMap(targets[targets.length - 1]);
  }


  @VisibleForTesting
  static class MyPublicResourceIdMapBuilder implements NanoXmlBuilder {
    private final TIntObjectHashMap<String> myIdMap = new TIntObjectHashMap<>(3000);

    private String myName;
    private String myType;
    private int myId;
    private boolean inGroup;

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) {
      if ("public".equals(name) && myName != null && myType != null) {
        if (myId != 0) {
          myIdMap.put(myId, SdkConstants.ANDROID_PREFIX + myType + "/" + myName);

          // Within <public-group> we increase the id based on a given first id.
          if (inGroup) {
            myId++;
          }
        }
      }
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
      switch (key) {
        case "name":
          myName = value;
          break;
        case "type":
          myType = value;
          break;
        case "first-id":
        case "id":
          try {
            myId = Integer.decode(value);
          } catch (NumberFormatException e) {
            myId = 0;
          }
          break;
      }
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
      if (!inGroup) {
        // This is a top-level <attr> so clear myType and myId
        myType = null;
        myId = 0;
      }

      if ("public-group".equals(name)) {
        inGroup = true;
      }

      myName = null;
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) {
      if ("public-group".equals(name)) {
        inGroup = false;
      }
    }

    public TIntObjectHashMap<String> getIdMap() {
      return myIdMap;
    }
  }
}
