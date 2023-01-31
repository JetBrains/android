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
package org.jetbrains.android.sdk;

import com.android.SdkConstants;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.LayoutLibraryLoader;
import com.android.tools.idea.layoutlib.RenderingException;
import com.android.tools.idea.res.FrameworkResourceRepositoryManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.internal.statistic.analytics.StudioCrashDetails;
import com.intellij.internal.statistic.analytics.StudioCrashDetection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl;
import org.jetbrains.android.resourceManagers.FilteredAttributeDefinitions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidTargetData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidTargetData");

  @NotNull private final List<String> myPublicFileNames = ImmutableList.of("public.xml", "public-final.xml", "public-staging.xml");

  private final AndroidSdkData mySdkData;
  private final IAndroidTarget myTarget;

  private final Object myAttrDefsLock = new Object();
  @GuardedBy("myAttrDefsLock")
  private AttributeDefinitions myAttrDefs;

  private LayoutLibrary myLayoutLibrary;

  private final Object myPublicResourceIdMapLock = new Object();
  @GuardedBy("myPublicResourceIdMapLock")
  private Int2ObjectMap<String> myPublicResourceIdMap;

  private volatile MyStaticConstantsData myStaticConstantsData;

  public AndroidTargetData(@NotNull AndroidSdkData sdkData, @NotNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  /**
   * Filters attributes through the public.xml file
   */
  @NotNull
  public AttributeDefinitions getPublicAttrDefs(@NotNull Project project) {
    AttributeDefinitions attrDefs = getAllAttrDefs(project);
    return new PublicAttributeDefinitions(attrDefs);
  }

  /**
   * Returns all attributes
   */
  @NotNull
  public AttributeDefinitions getAllAttrDefs(@NotNull Project project) {
    synchronized (myAttrDefsLock) {
      if (myAttrDefs == null) {
        String attrsPath = FileUtil.toSystemIndependentName(myTarget.getPath(IAndroidTarget.ATTRIBUTES).toString());
        String attrsManifestPath = FileUtil.toSystemIndependentName(myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES).toString());
        myAttrDefs = AttributeDefinitionsImpl.parseFrameworkFiles(new File(attrsPath), new File(attrsManifestPath));
      }
      return myAttrDefs;
    }
  }

  // TODO: Consider moving this method to FrameworkResourceRepository.
  @Nullable
  public Int2ObjectMap<String> getPublicIdMap() {
    synchronized (myPublicResourceIdMapLock) {
      if (myPublicResourceIdMap == null) {
        buildPublicResourceIdMap();
      }
      return myPublicResourceIdMap;
    }
  }

  public boolean isResourcePublic(@NotNull ResourceType type, @NotNull String name) {
    ResourceRepository frameworkResources = getFrameworkResources(Collections.emptySet());
    if (frameworkResources == null) {
      return false;
    }
    List<ResourceItem> resources = frameworkResources.getResources(ResourceNamespace.ANDROID, type, name);
    return !resources.isEmpty() && ((ResourceItemWithVisibility)resources.get(0)).getVisibility() == ResourceVisibility.PUBLIC;
  }

  private void buildPublicResourceIdMap() {
    Path resDirPath = myTarget.getPath(IAndroidTarget.RESOURCES);

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
    synchronized (myPublicResourceIdMapLock) {
      myPublicResourceIdMap = resourceIdMap;
    }
  }

  private static boolean isCrashCausedByLayoutlib(@NotNull StudioCrashDetails crash) {
    return crash.isJvmCrash() &&
           (crash.getErrorThread().contains("Layoutlib Render Thread") || crash.getErrorFrame().contains("libandroid_runtime"));
  }

  private static boolean hasStudioCrash() {
    List<StudioCrashDetails> crashes = StudioCrashDetection.reapCrashDescriptions();
    for (StudioCrashDetails crash : crashes) {
      if (isCrashCausedByLayoutlib(crash)) {
        return true;
      }
    }
    return false;
  }

  @Slow
  @NotNull
  public LayoutLibrary getLayoutLibrary(@NotNull Project project) throws RenderingException {
    if (myLayoutLibrary == null || myLayoutLibrary.isDisposed()) {
      if (myTarget instanceof CompatibilityRenderTarget) {
        IAndroidTarget target = ((CompatibilityRenderTarget)myTarget).getRenderTarget();
        AndroidTargetData targetData = mySdkData.getTargetData(target);
        if (targetData != this) {
          myLayoutLibrary = targetData.getLayoutLibrary(project);
          return myLayoutLibrary;
        }
      }

      if (!(myTarget instanceof StudioEmbeddedRenderTarget)) {
        LOG.warn("Rendering will not use the StudioEmbeddedRenderTarget");
      }
      myLayoutLibrary = LayoutLibraryLoader.load(myTarget, getFrameworkEnumValues(), AndroidTargetData::hasStudioCrash);
      Disposer.register(((ProjectEx)project).getEarlyDisposable(), myLayoutLibrary);
    }

    return myLayoutLibrary;
  }

  /**
   * The keys of the returned map are attr names. The values are maps defining numerical values of the corresponding enums or flags.
   */
  @Slow
  @NotNull
  private Map<String, Map<String, Integer>> getFrameworkEnumValues() {
    ResourceRepository resources = getFrameworkResources(ImmutableSet.of());
    if (resources == null) {
      return Collections.emptyMap();
    }

    Map<String, Map<String, Integer>> result = new HashMap<>();
    Collection<ResourceItem> items = resources.getResources(ResourceNamespace.ANDROID, ResourceType.ATTR).values();
    for (ResourceItem item: items) {
      ResourceValue attr = item.getResourceValue();
      if (attr instanceof AttrResourceValue) {
        Map<String, Integer> valueMap = ((AttrResourceValue)attr).getAttributeValues();
        if (!valueMap.isEmpty()) {
          result.put(attr.getName(), valueMap);
        }
      }
    }

    items = resources.getResources(ResourceNamespace.ANDROID, ResourceType.STYLEABLE).values();
    for (ResourceItem item: items) {
      ResourceValue styleable = item.getResourceValue();
      if (styleable instanceof StyleableResourceValue) {
        List<AttrResourceValue> attrs = ((StyleableResourceValue)styleable).getAllAttributes();
        for (AttrResourceValue attr: attrs) {
          Map<String, Integer> valueMap = attr.getAttributeValues();
          if (!valueMap.isEmpty()) {
            result.put(attr.getName(), valueMap);
          }
        }
      }
    }
    return result;
  }

  public void clearLayoutBitmapCache(Module module) {
    if (myLayoutLibrary != null) {
      myLayoutLibrary.clearResourceCaches(module);
    }
  }

  public void clearFontCache(String path) {
    if (myLayoutLibrary != null) {
      myLayoutLibrary.clearFontCache(path);
    }
  }

  public void clearAllCaches(Module module) {
    if (myLayoutLibrary != null) {
      myLayoutLibrary.clearAllCaches(module);
    }
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @NotNull
  public synchronized MyStaticConstantsData getStaticConstantsData() {
    if (myStaticConstantsData == null) {
      myStaticConstantsData = new MyStaticConstantsData();
    }
    return myStaticConstantsData;
  }

  /**
   * Returns a repository of framework resources for the Android target. The returned repository is guaranteed
   * to contain resources for the given set of languages plus the language-neutral ones, but may contain resources
   * for more languages than was requested. The repository loads faster if the set of languages is smaller.
   *
   * @param languages a set of ISO 639 language codes
   * @return the repository of Android framework resources, or null if the resources directory or file
   *     does not exist on disk
   */
  @Slow
  @Nullable
  public synchronized ResourceRepository getFrameworkResources(@NotNull Set<String> languages) {
    Path resFolderOrJar = myTarget.getPath(IAndroidTarget.RESOURCES);
    if (!Files.exists(resFolderOrJar)) {
      LOG.error(String.format("\"%s\" directory or file cannot be found", resFolderOrJar));
      return null;
    }

    return FrameworkResourceRepositoryManager.getInstance().getFrameworkResources(
      resFolderOrJar,
      myTarget instanceof CompatibilityRenderTarget,
      languages);
  }

  /**
   * This method can return null when the user is changing the SDK setting in their project.
   */
  @Nullable
  public static AndroidTargetData getTargetData(@NotNull IAndroidTarget target, @NotNull Module module) {
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    return platform != null ? platform.getSdkData().getTargetData(target) : null;
  }

  private class PublicAttributeDefinitions extends FilteredAttributeDefinitions {
    protected PublicAttributeDefinitions(@NotNull AttributeDefinitions wrappee) {
      super(wrappee);
    }

    @Override
    protected boolean isAttributeAcceptable(@NotNull ResourceReference attr) {
      return attr.getNamespace().equals(ResourceNamespace.ANDROID)
             && !attr.getName().startsWith("__removed")
             && isResourcePublic(ResourceType.ATTR, attr.getName());
    }
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

  public class MyStaticConstantsData {
    private final Set<String> myActivityActions;
    private final Set<String> myServiceActions;
    private final Set<String> myReceiverActions;
    private final Set<String> myCategories;

    private MyStaticConstantsData() {
      myActivityActions = collectValues(IAndroidTarget.ACTIONS_ACTIVITY);
      myServiceActions = collectValues(IAndroidTarget.ACTIONS_SERVICE);
      myReceiverActions = collectValues(IAndroidTarget.ACTIONS_BROADCAST);
      myCategories = collectValues(IAndroidTarget.CATEGORIES);
    }

    @Nullable
    public Set<String> getActivityActions() {
      return myActivityActions;
    }

    @Nullable
    public Set<String> getServiceActions() {
      return myServiceActions;
    }

    @Nullable
    public Set<String> getReceiverActions() {
      return myReceiverActions;
    }

    @Nullable
    public Set<String> getCategories() {
      return myCategories;
    }

    @Nullable
    private Set<String> collectValues(int pathId) {
      try (BufferedReader reader = Files.newBufferedReader(myTarget.getPath(pathId))) {
        Set<String> result = new HashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();

          if (!line.isEmpty() && !line.startsWith("#")) {
            result.add(line);
          }
        }
        return result;
      }
      catch (IOException e) {
        return null;
      }
    }
  }
}
