/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.sdk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.TestOnly;
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
import com.android.tools.environment.Logger;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.LayoutLibraryLoader;
import com.android.tools.idea.layoutlib.RenderingException;
import com.android.tools.res.FrameworkOverlay;
import com.android.tools.res.FrameworkResourceRepositoryManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.lang.ref.SoftReference;
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
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.android.tools.dom.attrs.AttributeDefinitions;
import com.android.tools.dom.attrs.AttributeDefinitionsImpl;
import com.android.tools.dom.attrs.FilteredAttributeDefinitions;

public class AndroidTargetData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidTargetData");

  private final AndroidSdkData mySdkData;
  private final IAndroidTarget myTarget;

  private final Object myAttrDefsLock = new Object();
  @GuardedBy("myAttrDefsLock")
  private AttributeDefinitions myAttrDefs;

  private LayoutLibrary myLayoutLibrary;

  private volatile MyStaticConstantsData myStaticConstantsData;

  @VisibleForTesting
  public AndroidTargetData(@NonNull AndroidSdkData sdkData, @NonNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  /**
   * Filters attributes through the public.xml file
   */
  @NonNull
  public AttributeDefinitions getPublicAttrDefs() {
    AttributeDefinitions attrDefs = getAllAttrDefs();
    return new PublicAttributeDefinitions(attrDefs);
  }

  /**
   * Returns all attributes
   */
  @NonNull
  public AttributeDefinitions getAllAttrDefs() {
    synchronized (myAttrDefsLock) {
      if (myAttrDefs == null) {
        // to system independent paths
        String attrsPath = myTarget.getPath(IAndroidTarget.ATTRIBUTES).toString().replace('\\', '/');
        String attrsManifestPath = myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES).toString().replace('\\', '/');
        myAttrDefs = AttributeDefinitionsImpl.parseFrameworkFiles(new File(attrsPath), new File(attrsManifestPath));
      }
      return myAttrDefs;
    }
  }

  public boolean isResourcePublic(@NonNull ResourceType type, @NonNull String name) {
    ResourceRepository frameworkResources = getFrameworkResources(Collections.emptySet(), Collections.emptyList());
    if (frameworkResources == null) {
      return false;
    }
    List<ResourceItem> resources = frameworkResources.getResources(ResourceNamespace.ANDROID, type, name);
    return !resources.isEmpty() && ((ResourceItemWithVisibility)resources.get(0)).getVisibility() == ResourceVisibility.PUBLIC;
  }

  @Slow
  @NonNull
  LayoutLibrary getLayoutLibrary(
    @NonNull Consumer<LayoutLibrary> register,
    @NonNull Supplier<Boolean> hasLayoutlibCrash) throws RenderingException {
    if (myLayoutLibrary == null || myLayoutLibrary.isDisposed()) {
      if (myTarget instanceof CompatibilityRenderTarget) {
        IAndroidTarget target = ((CompatibilityRenderTarget)myTarget).getRenderTarget();
        AndroidTargetData targetData = AndroidTargetData.get(mySdkData, target);
        if (targetData != this) {
          myLayoutLibrary = targetData.getLayoutLibrary(register, hasLayoutlibCrash);
          return myLayoutLibrary;
        }
      }

      if (!(myTarget instanceof EmbeddedRenderTarget)) {
        LOG.warn("Rendering will not use the EmbeddedRenderTarget");
      }
      myLayoutLibrary = LayoutLibraryLoader.load(myTarget, getFrameworkEnumValues(), hasLayoutlibCrash);
      register.accept(myLayoutLibrary);
    }

    return myLayoutLibrary;
  }

  /**
   * The keys of the returned map are attr names. The values are maps defining numerical values of the corresponding enums or flags.
   */
  @Slow
  @NonNull
  private Map<String, Map<String, Integer>> getFrameworkEnumValues() {
    ResourceRepository resources = getFrameworkResources(ImmutableSet.of(), ImmutableList.of());
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

  public void clearLayoutBitmapCache(Object moduleKey) {
    if (myLayoutLibrary != null) {
      myLayoutLibrary.clearResourceCaches(moduleKey);
    }
  }

  public void clearFontCache(String path) {
    if (myLayoutLibrary != null) {
      myLayoutLibrary.clearFontCache(path);
    }
  }

  public void clearAllCaches(Object moduleKey) {
    if (myLayoutLibrary != null) {
      myLayoutLibrary.clearAllCaches(moduleKey);
    }
  }

  @NonNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @NonNull
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
   * @param overlays a list of overlays to add to the base framework resources
   * @return the repository of Android framework resources, or null if the resources directory or file
   *     does not exist on disk
   */
  @Slow
  @Nullable
  public synchronized ResourceRepository getFrameworkResources(@NonNull Set<String> languages,
                                                               @NonNull List<? extends FrameworkOverlay> overlays) {
    Path resFolderOrJar = myTarget.getPath(IAndroidTarget.RESOURCES);
    if (!Files.exists(resFolderOrJar)) {
      LOG.error(String.format("\"%s\" directory or file cannot be found", resFolderOrJar));
      return null;
    }

    return FrameworkResourceRepositoryManager.getInstance().getFrameworkResources(
      resFolderOrJar,
      myTarget instanceof CompatibilityRenderTarget,
      languages,
      overlays);
  }

  /**
   * This method can return null when the user is changing the SDK setting in their project.
   */
  @Nullable
  public static AndroidTargetData getTargetData(@NonNull IAndroidTarget target, @Nullable AndroidPlatform platform) {
    return platform != null ? AndroidTargetData.get(platform.getSdkData(), target) : null;
  }

  private class PublicAttributeDefinitions extends FilteredAttributeDefinitions {
    protected PublicAttributeDefinitions(@NonNull AttributeDefinitions wrappee) {
      super(wrappee);
    }

    @Override
    protected boolean isAttributeAcceptable(@NonNull ResourceReference attr) {
      return attr.getNamespace().equals(ResourceNamespace.ANDROID)
             && !attr.getName().startsWith("__removed")
             && isResourcePublic(ResourceType.ATTR, attr.getName());
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

  private static final Map<AndroidSdkData, Map<String, SoftReference<AndroidTargetData>>> myTargetDataCache = new WeakHashMap<>();

  public static AndroidTargetData get(@NonNull AndroidSdkData sdk, @NonNull IAndroidTarget target) {
    Map<String, SoftReference<AndroidTargetData>> targetDataByTarget = myTargetDataCache.computeIfAbsent(sdk, s -> Maps.newHashMap());
    String key = target.hashString();
    final SoftReference<AndroidTargetData> targetDataRef = targetDataByTarget.get(key);
    AndroidTargetData targetData = targetDataRef != null ? targetDataRef.get() : null;
    if (targetData == null) {
      targetData = new AndroidTargetData(sdk, target);
      targetDataByTarget.put(key, new SoftReference<>(targetData));
    }
    return targetData;
  }

  @TestOnly
  public static void clearCache() {
    myTargetDataCache.clear();
  }
}
