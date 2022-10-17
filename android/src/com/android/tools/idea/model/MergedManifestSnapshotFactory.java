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
import static com.android.SdkConstants.ATTR_VERSION_CODE;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_PERMISSION;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.checks.PermissionRequirement.ATTR_PROTECTION_LEVEL;
import static com.android.tools.lint.checks.PermissionRequirement.VALUE_DANGEROUS;
import static com.android.xml.AndroidManifest.ATTRIBUTE_DEBUGGABLE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_HASCODE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_ICON;
import static com.android.xml.AndroidManifest.ATTRIBUTE_LABEL;
import static com.android.xml.AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION;
import static com.android.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_PACKAGE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_PARENT_ACTIVITY_NAME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_SUPPORTS_RTL;
import static com.android.xml.AndroidManifest.ATTRIBUTE_TARGET_SDK_VERSION;
import static com.android.xml.AndroidManifest.ATTRIBUTE_THEME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_UI_OPTIONS;
import static com.android.xml.AndroidManifest.ATTRIBUTE_VALUE;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_METADATA;
import static com.android.xml.AndroidManifest.NODE_SERVICE;
import static com.android.xml.AndroidManifest.NODE_USES_SDK;
import static com.android.xml.AndroidManifest.VALUE_PARENT_ACTIVITY;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.manifmerger.Actions;
import com.android.manifmerger.MergingReport;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Factory of {@link MergedManifestSnapshot}. The created snapshots represent the merged manifest state at a
 * point in time and are immutable.
 */
class MergedManifestSnapshotFactory {

  /**
   * A resource value defined by the manifest. Unlike its base class, does not need to keep a reference
   * to an XML DOM node in order to resolve the resource value to a {@link ResourceReference}.
   */
  private static class ManifestResourceValue extends ResourceValueImpl {
    @Nullable private final ResourceReference myReference;

    ManifestResourceValue(@NotNull ResourceNamespace namespace,
                          @NotNull ResourceType type,
                          @NotNull String name,
                          @Nullable String value,
                          @Nullable ResourceReference reference) {
      super(namespace, type, name, value);
      myReference = reference;
    }

    @Override
    @Nullable
    public ResourceReference getReference() {
      return myReference;
    }
  }

  @Nullable
  private static String getAttributeValue(@NotNull Element element,
                                          @Nullable String namespaceUri,
                                          @NotNull String attributeName) {
    return Strings.emptyToNull(element.getAttributeNS(namespaceUri, attributeName));
  }

  @Nullable
  private static ResourceValue getResourceValue(@NotNull ResourceNamespace namespace,
                                                @NotNull ResourceType type,
                                                @NotNull Element element,
                                                @Nullable String namespaceUri,
                                                @NotNull String attributeName) {
    String value = getAttributeValue(element, namespaceUri, attributeName);
    if (value == null) {
      return null;
    }
    ResourceUrl url = ResourceUrl.parse(value);
    ResourceReference reference =
      url == null ? null : url.resolve(namespace, namespacePrefix -> element.lookupNamespaceURI(namespacePrefix));
    return new ManifestResourceValue(namespace, type, attributeName, value, reference);
  }

  private static AndroidVersion getApiVersion(@NotNull Element usesSdk,
                                              @NotNull String attribute,
                                              @NotNull AndroidVersion defaultApiLevel) {
    String valueString = getAttributeValue(usesSdk, ANDROID_URI, attribute);
    if (valueString != null) {
      // TODO: Pass in platforms if we have them
      AndroidVersion version = SdkVersionInfo.getVersion(valueString, null);
      if (version != null) {
        return version;
      }
    }
    return defaultApiLevel;
  }

  /**
   * @deprecated This method only exists to preserve the behavior of legacy callers of
   * {@link MergedManifestManager#getFreshSnapshot}. If we encounter an exception during
   * merging or parsing the result, we should just allow that exception to propagate up
   * to the caller.
   */
  @Deprecated
  @NotNull
  static MergedManifestSnapshot createEmptyMergedManifestSnapshot(@NotNull Module module) {
    return new MergedManifestSnapshot(module,
                                      null,
                                      null,
                                      null,
                                      ImmutableMap.of(),
                                      null,
                                      AndroidVersion.DEFAULT,
                                      AndroidVersion.DEFAULT,
                                      null,
                                      null,
                                      false,
                                      null,
                                      null,
                                      null,
                                      ImmutablePermissionHolder.EMPTY,
                                      false,
                                      ImmutableList.of(),
                                      ImmutableList.of(),
                                      ImmutableList.of(),
                                      null,
                                      ImmutableList.of(),
                                      false
    );
  }


  @NotNull
  static MergedManifestSnapshot createMergedManifestSnapshot(@NotNull AndroidFacet facet, @NotNull MergedManifestInfo mergedManifestInfo) {
    try {
      Document document = mergedManifestInfo.getXmlDocument();
      Element root = document == null ? null : document.getDocumentElement();
      if (root == null) {
        throw new MergedManifestException.MissingElement(TAG_MANIFEST, mergedManifestInfo);
      }

      // The package comes from the main manifest, NOT from the merged manifest.
      final String appId = getAttributeValue(root, null, ATTRIBUTE_PACKAGE);
      final String packageName = ProjectSystemUtil.getModuleSystem(facet).getPackageName();
      if (packageName == null) {
        throw new MergedManifestException.MissingAttribute(TAG_MANIFEST, null, ATTRIBUTE_PACKAGE, mergedManifestInfo);
      }

      Namespacing namespacing = ResourceRepositoryManager.getInstance(facet).getNamespacing();
      ResourceNamespace namespace =
        namespacing == Namespacing.DISABLED ? ResourceNamespace.RES_AUTO : ResourceNamespace.fromPackageName(packageName);

      String versionCodeStr = getAttributeValue(root, ANDROID_URI, ATTR_VERSION_CODE);
      Integer versionCode = null;
      try {
        versionCode = versionCodeStr != null ? Integer.valueOf(versionCodeStr) : null;
      }
      catch (NumberFormatException ignored) {
      }

      ResourceValue appIcon = null;
      ResourceValue appLabel = null;
      String manifestTheme = null;
      boolean supportsRtl = false;
      Boolean isAppDebuggable = null;
      boolean appHasCode = true;
      HashMap<String, ActivityAttributesSnapshot> activityAttributesMap = new HashMap<>();
      ArrayList<Element> activities = new ArrayList<>();
      ArrayList<Element> activityAliases = new ArrayList<>(4);
      ArrayList<Element> services = new ArrayList<>(4);
      AndroidVersion targetSdk = AndroidVersion.DEFAULT;
      AndroidVersion minSdk = AndroidVersion.DEFAULT;
      Set<String> permissions = Sets.newHashSetWithExpectedSize(30);
      Set<String> revocable = Sets.newHashSetWithExpectedSize(2);

      Node node = root.getFirstChild();
      while (node != null) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          String nodeName = node.getNodeName();
          if (NODE_APPLICATION.equals(nodeName)) {
            Element application = (Element)node;
            appIcon = getResourceValue(namespace, ResourceType.DRAWABLE, application, ANDROID_URI, ATTRIBUTE_ICON);
            appLabel = getResourceValue(namespace, ResourceType.STRING, application, ANDROID_URI, ATTRIBUTE_LABEL);
            manifestTheme = getAttributeValue(application, ANDROID_URI, ATTRIBUTE_THEME);
            supportsRtl = VALUE_TRUE.equals(getAttributeValue(application, ANDROID_URI, ATTRIBUTE_SUPPORTS_RTL));

            String debuggable = getAttributeValue(application, ANDROID_URI, ATTRIBUTE_DEBUGGABLE);
            isAppDebuggable = debuggable == null ? null : VALUE_TRUE.equals(debuggable);

            String hasCode = getAttributeValue(application, ANDROID_URI, ATTRIBUTE_HASCODE);
            appHasCode = hasCode == null || VALUE_TRUE.equals(hasCode);

            Node child = node.getFirstChild();
            while (child != null) {
              if (child.getNodeType() == Node.ELEMENT_NODE) {
                String childNodeName = child.getNodeName();
                if (NODE_ACTIVITY.equals(childNodeName)) {
                  Element element = (Element)child;
                  ActivityAttributesSnapshot attributes = createActivityAttributesSnapshot(element, packageName, namespace);
                  activityAttributesMap.put(attributes.getName(), attributes);
                  activities.add(element);
                }
                else if (NODE_ACTIVITY_ALIAS.equals(childNodeName)) {
                  activityAliases.add((Element)child);
                }
                else if (NODE_SERVICE.equals(childNodeName)) {
                  services.add((Element)child);
                }
              }
              child = child.getNextSibling();
            }
          }
          else if (NODE_USES_SDK.equals(nodeName)) {
            // Look up target SDK
            Element usesSdk = (Element)node;
            minSdk = getApiVersion(usesSdk, ATTRIBUTE_MIN_SDK_VERSION, AndroidVersion.DEFAULT);
            targetSdk = getApiVersion(usesSdk, ATTRIBUTE_TARGET_SDK_VERSION, minSdk);
          }
          else if (TAG_USES_PERMISSION.equals(nodeName)
                   || TAG_USES_PERMISSION_SDK_23.equals(nodeName)
                   || TAG_USES_PERMISSION_SDK_M.equals(nodeName)) {
            Element element = (Element)node;
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (!name.isEmpty()) {
              permissions.add(name);
            }
          }
          else if (nodeName.equals(TAG_PERMISSION)) {
            Element element = (Element)node;
            String protectionLevel = element.getAttributeNS(ANDROID_URI,
                                                            ATTR_PROTECTION_LEVEL);
            if (VALUE_DANGEROUS.equals(protectionLevel)) {
              String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
              if (!name.isEmpty()) {
                revocable.add(name);
              }
            }
          }
        }

        node = node.getNextSibling();
      }

      AndroidVersion modelMinSdk = null;
      AndroidVersion modelTargetSdk = null;
      AndroidModel androidModel = AndroidModel.get(facet);
      if (androidModel != null) {
        modelMinSdk = androidModel.getMinSdkVersion();
        modelTargetSdk = androidModel.getTargetSdkVersion();
        // Else: not specified in gradle files; fall back to manifest
      }

      ImmutablePermissionHolder permissionHolder = new ImmutablePermissionHolder(
        modelMinSdk == null ? minSdk : modelMinSdk,
        modelTargetSdk == null ? targetSdk : modelTargetSdk,
        ImmutableSet.copyOf(permissions),
        ImmutableSet.copyOf(revocable));

        Actions actions = mergedManifestInfo.getActions();
        ImmutableList<MergingReport.Record> loggingRecords = mergedManifestInfo.getLoggingRecords();
        return new MergedManifestSnapshot(facet.getModule(), packageName, versionCode, manifestTheme,
                                          ImmutableMap.copyOf(activityAttributesMap),
                                          mergedManifestInfo, minSdk, targetSdk, appIcon, appLabel, supportsRtl, isAppDebuggable, document,
                                          ImmutableList.copyOf(mergedManifestInfo.getFiles()),
                                          permissionHolder, appHasCode,
                                          ImmutableList.copyOf(activities),
                                          ImmutableList.copyOf(activityAliases),
                                          ImmutableList.copyOf(services), actions, loggingRecords, true);
    }
    catch (MergedManifestException|ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new MergedManifestException.ParsingError(mergedManifestInfo, e);
    }
  }

  @NotNull
  private static ActivityAttributesSnapshot createActivityAttributesSnapshot(@NotNull Element activity,
                                                                             @Nullable String packageName,
                                                                             @NotNull ResourceNamespace namespace) {
    // Get activity name.
    String name = getAttributeValue(activity, ANDROID_URI, ATTRIBUTE_NAME);
    if (name == null || name.isEmpty()) {
      throw new RuntimeException("Activity name cannot be empty.");
    }
    int index = name.indexOf('.');
    if (index <= 0 && packageName != null && !packageName.isEmpty()) {
      name = packageName + (index == -1 ? "." : "") + name;
    }

    // Get activity icon.
    ResourceValue icon = getResourceValue(namespace, ResourceType.DRAWABLE, activity, ANDROID_URI, ATTRIBUTE_ICON);

    // Get activity label.
    ResourceValue label = getResourceValue(namespace, ResourceType.STRING, activity, ANDROID_URI, ATTRIBUTE_LABEL);

    // Get activity parent. Also search the meta-data for parent info.
    String value = getAttributeValue(activity, ANDROID_URI, ATTRIBUTE_PARENT_ACTIVITY_NAME);
    if (value == null || value.isEmpty()) {
      Node child = activity.getFirstChild();
      // TODO: Not sure if meta data can be used for API Level > 16
      while (child != null) {
        if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(NODE_METADATA)) {
          String metaDataName = getAttributeValue((Element)child, ANDROID_URI, ATTRIBUTE_NAME);
          if (VALUE_PARENT_ACTIVITY.equals(metaDataName)) {
            value = getAttributeValue(activity, ANDROID_URI, ATTRIBUTE_VALUE);
            if (value != null) {
              index = value.indexOf('.');
              if (index <= 0 && packageName != null && !packageName.isEmpty()) {
                value = packageName + (index == -1 ? "." : "") + value;
                break;
              }
            }
          }
        }
        child = child.getNextSibling();
      }
    }

    String parentActivity = StringUtil.isNotEmpty(value) ? value : null;
    // Get activity theme.
    value = getAttributeValue(activity, ANDROID_URI, ATTRIBUTE_THEME);
    String theme = StringUtil.isNotEmpty(value) ? value : null;

    // Get UI options.
    value = getAttributeValue(activity, ANDROID_URI, ATTRIBUTE_UI_OPTIONS);
    String uiOptions = StringUtil.isNotEmpty(value) ? value : null;

    return new ActivityAttributesSnapshot(activity, icon, label, name, parentActivity, theme, uiOptions);
  }
}
