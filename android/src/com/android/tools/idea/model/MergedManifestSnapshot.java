/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.manifmerger.Actions;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlNode;
import com.android.resources.ScreenSize;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * An immutable snapshot of the merged manifests at a point in time.
 */
public class MergedManifestSnapshot {
  private final long myCreationTimeMs = Clock.getTime();
  private final Module myModule;
  @Nullable private final String myPackageName;
  @Nullable private final String myApplicationId;
  @Nullable private final Integer myVersionCode;
  @Nullable private final Actions myActions;
  @NotNull private final ImmutableList<MergingReport.Record> myLoggingRecords;
  @Nullable private String myManifestTheme;
  @NotNull private final ImmutableMap<String, ActivityAttributesSnapshot> myAttributes;
  @Nullable private final MergedManifestInfo myMergedManifestInfo;
  @NotNull private final AndroidVersion myMinSdk;
  @NotNull private final AndroidVersion myTargetSdk;
  @Nullable private final ResourceValue myAppIcon;
  @Nullable private final ResourceValue myAppLabel;
  private final boolean mySupportsRtl;
  @Nullable private final Boolean myIsDebuggable;
  @NotNull private final ImmutableMap<String, XmlNode.NodeKey> myNodeKeys;
  @Nullable private final Document myDocument;
  @NotNull private final ImmutableList<VirtualFile> myFiles;
  @NotNull private final ImmutablePermissionHolder myPermissions;
  private final boolean myAppHasCode;
  private final ImmutableList<Element> myActivities;
  private final ImmutableList<Element> myActivityAliases;
  private final ImmutableList<Element> myServices;
  private final boolean myIsValid;


  MergedManifestSnapshot(@NotNull Module module,
                         @Nullable String packageName,
                         @Nullable String applicationId,
                         @Nullable Integer versionCode,
                         @Nullable String manifestTheme,
                         @NotNull ImmutableMap<String, ActivityAttributesSnapshot> activityAttributes,
                         @Nullable MergedManifestInfo mergedManifestInfo,
                         @NotNull AndroidVersion minSdk,
                         @NotNull AndroidVersion targetSdk,
                         @Nullable ResourceValue appIcon,
                         @Nullable ResourceValue appLabel,
                         boolean supportsRtl,
                         @Nullable Boolean isDebuggable,
                         @Nullable Document document,
                         @Nullable ImmutableList<VirtualFile> manifestFiles,
                         @NotNull ImmutablePermissionHolder permissions,
                         boolean appHasCode,
                         @NotNull ImmutableList<Element> activities,
                         @NotNull ImmutableList<Element> activityAliases,
                         @NotNull ImmutableList<Element> services,
                         @Nullable Actions actions,
                         @NotNull ImmutableList<MergingReport.Record> loggingRecords,
                         boolean isValid) {
    myModule = module;
    myPackageName = packageName;
    myApplicationId = applicationId;
    myVersionCode = versionCode;
    myManifestTheme = manifestTheme;
    myAttributes = activityAttributes;
    myMergedManifestInfo = mergedManifestInfo;
    myMinSdk = minSdk;
    myTargetSdk = targetSdk;
    myAppIcon = appIcon;
    myAppLabel = appLabel;
    mySupportsRtl = supportsRtl;
    myIsDebuggable = isDebuggable;
    myDocument = document;
    myFiles = manifestFiles != null ? manifestFiles : ImmutableList.of();
    myPermissions = permissions;
    myAppHasCode = appHasCode;
    myActivities = activities;
    myActivityAliases = activityAliases;
    myServices = services;
    myLoggingRecords = loggingRecords;
    myActions = actions;
    myIsValid = isValid;

    if (actions != null) {
      ImmutableMap.Builder<String, XmlNode.NodeKey> nodeKeysBuilder = ImmutableMap.builder();
      Set<XmlNode.NodeKey> keys = myActions.getNodeKeys();
      for (XmlNode.NodeKey key : keys) {
        nodeKeysBuilder.put(key.toString(), key);
      }
      myNodeKeys = nodeKeysBuilder.build();
    }
    else {
      myNodeKeys = ImmutableMap.of();
    }
  }

  /**
   * Returns false if the manifest merger encountered any errors when computing this snapshot,
   * indicating that this snapshot contains dummy values that may not represent the merged
   * manifest accurately.
   */
  public boolean isValid() {
    return myIsValid;
  }

  long getCreationTimestamp() {
    return myCreationTimeMs;
  }

  @Nullable
  MergedManifestInfo getMergedManifestInfo() {
    return myMergedManifestInfo;
  }

  @Nullable
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public List<VirtualFile> getManifestFiles() {
    return myFiles;
  }

  @Nullable
  public String getPackage() {
    return myPackageName;
  }

  @Nullable
  public String getApplicationId() {
    return myApplicationId;
  }

  @Nullable
  public Integer getVersionCode() {
    return myVersionCode;
  }

  @NotNull
  public Map<String, ActivityAttributesSnapshot> getActivityAttributesMap() {
    return myAttributes;
  }

  @Nullable
  public ActivityAttributesSnapshot getActivityAttributes(@NotNull String activity) {
    int index = activity.indexOf('.');
    if (index <= 0 && myApplicationId != null && !myApplicationId.isEmpty()) {
      activity = myApplicationId + (index == -1 ? "." : "") + activity;
    }
    return getActivityAttributesMap().get(activity);
  }

  @Nullable
  public String getManifestTheme() {
    return myManifestTheme;
  }

  @NotNull
  public String getDefaultTheme(@Nullable IAndroidTarget renderingTarget, @Nullable ScreenSize screenSize, @Nullable Device device) {
    if (myManifestTheme != null) {
      return myManifestTheme;
    }

    // For Android Wear and Android TV, the defaults differ
    if (device != null) {
      if (HardwareConfigHelper.isWear(device)) {
        return "@android:style/Theme.DeviceDefault.Light";
      }
      else if (HardwareConfigHelper.isTv(device)) {
        return "@style/Theme.Leanback";
      }
    }


    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      // Should not happen, but has been observed to happen in rare scenarios
      // (such as 73332530), probably related to race condition between
      // Gradle sync and layout rendering
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Material.Light";
    }

    // From manifest theme documentation:
    // "If that attribute is also not set, the default system theme is used."
    int targetSdk = getTargetSdkVersion().getApiLevel();
    AndroidModel androidModel = AndroidModel.get(facet);
    if (androidModel != null) {
      AndroidVersion targetSdkVersion = androidModel.getTargetSdkVersion();
      if (targetSdkVersion != null) {
        targetSdk = targetSdkVersion.getApiLevel();
      }
    }

    int renderingTargetSdk = targetSdk;
    if (renderingTarget instanceof CompatibilityRenderTarget) {
      renderingTargetSdk = renderingTarget.getVersion().getApiLevel();
      //targetSdk = SdkVersionInfo.HIGHEST_KNOWN_API
    }
    else if (renderingTarget != null) {
      renderingTargetSdk = renderingTarget.getVersion().getApiLevel();
    }

    int apiLevel = Math.min(targetSdk, renderingTargetSdk);
    if (apiLevel >= 21) {
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Material.Light"; //$NON-NLS-1$
    }
    else if (apiLevel >= 14 || apiLevel >= 11 && screenSize == ScreenSize.XLARGE) {
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Holo"; //$NON-NLS-1$
    }
    else {
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme"; //$NON-NLS-1$
    }
  }

  @Nullable
  public ResourceValue getApplicationIcon() {
    return myAppIcon;
  }

  @Nullable
  public ResourceValue getApplicationLabel() {
    return myAppLabel;
  }

  public boolean isRtlSupported() {
    return mySupportsRtl;
  }

  @Nullable
  public Boolean getApplicationDebuggable() {
    return myIsDebuggable;
  }

  public boolean getApplicationHasCode() {
    return myAppHasCode;
  }

  @NotNull
  public AndroidVersion getTargetSdkVersion() {
    return myTargetSdk;
  }

  @NotNull
  public AndroidVersion getMinSdkVersion() {
    return myMinSdk;
  }

  @NotNull
  public ImmutablePermissionHolder getPermissionHolder() {
    return myPermissions;
  }

  @NotNull
  public List<Element> getActivities() {
    return myActivities;
  }

  @NotNull
  public List<Element> getActivityAliases() {
    return myActivityAliases;
  }

  @NotNull
  public List<Element> getServices() {
    return myServices;
  }

  @Nullable
  public Element findUsedFeature(@NotNull String name) {
    if (myDocument == null) {
      return null;
    }

    Node node = myDocument.getDocumentElement().getFirstChild();
    while (node != null) {
      if (node.getNodeType() == Node.ELEMENT_NODE && NODE_USES_FEATURE.equals(node.getNodeName())) {
        Element element = (Element)node;
        if (name.equals(element.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
          return element;
        }
      }
      node = node.getNextSibling();
    }

    return null;
  }

  @NotNull
  public ImmutableList<MergingReport.Record> getLoggingRecords() {
    return myMergedManifestInfo != null ? myMergedManifestInfo.getLoggingRecords() : ImmutableList.of();
  }

  @Nullable
  public Actions getActions() {
    return myActions;
  }

  @Nullable
  public XmlNode.NodeKey getNodeKey(String name) {
    return myNodeKeys.get(name);
  }

  @Nullable
  public Element findActivity(@Nullable String qualifiedName) {
    if (qualifiedName == null || myActivities == null) {
      return null;
    }
    return getActivityOrAliasByName(qualifiedName, myActivities);
  }

  @Nullable
  public Element findActivityAlias(@Nullable String qualifiedName) {
    if (qualifiedName == null || myActivityAliases == null) {
      return null;
    }
    return getActivityOrAliasByName(qualifiedName, myActivityAliases);
  }

  @Nullable
  private static Element getActivityOrAliasByName(@NotNull String qualifiedName, @NotNull List<Element> activityOrAliasElements) {
    for (Element activity : activityOrAliasElements) {
      if (qualifiedName.equals(ActivityLocatorUtils.getQualifiedName(activity))) {
        return activity;
      }
    }

    return null;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }
}
