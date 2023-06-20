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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.manifmerger.Actions;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlNode;
import com.android.resources.ScreenSize;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.ThemeUtils;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * An immutable snapshot of the merged manifests at a point in time.
 */
public class MergedManifestSnapshot {
  private final Module myModule;
  @Nullable private final String myPackageName;
  @Nullable private final Integer myVersionCode;
  @Nullable private final Actions myActions;
  @NotNull private final ImmutableList<MergingReport.Record> myLoggingRecords;
  @Nullable private final String myManifestTheme;
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

    if (index <= 0 && myPackageName != null && !myPackageName.isEmpty()) {
      activity = myPackageName + (index == -1 ? "." : "") + activity;
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

    return ThemeUtils.getDefaultTheme(myModule, renderingTarget, screenSize, device);
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
