/*
 * Copyright (C) 2025 The Android Open Source Project
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
package org.jetbrains.android.dom.converters;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_PERMISSION;
import static com.android.SdkConstants.TAG_VALID_PURPOSE;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.sdk.AndroidPlatform;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.android.dom.manifest.UsesPermission;
import org.jetbrains.android.dom.manifest.UsesPermissionSdk23;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Provides completion for <purpose> tags in the manifest.
 */
public class AndroidPermissionPurposeConverter extends ResolvingConverter<String> {
  private static final Logger LOG = Logger.getInstance(AndroidPermissionPurposeConverter.class);

  // A map where the key is the build hash and the value is a map containing information in the
  // permission versions XML file from the SDK; the keys of the inner map are permission names
  // and values are a list of corresponding valid purposes for the permission.
  private static Map<String, Map<String, List<String>>> purposeMapCache = new ConcurrentHashMap<>();

  @NotNull
  @Override
  public Collection<String> getVariants(@NotNull ConvertContext context) {
    DomElement invocationElement = context.getInvocationElement();

    String permissionName = null;
    UsesPermission usesPermission = invocationElement.getParentOfType(UsesPermission.class, true);
    if (usesPermission != null) {
      permissionName = usesPermission.getName().getStringValue();
    }

    if (permissionName == null) {
      UsesPermissionSdk23 usesPermissionSdk23 =
        invocationElement.getParentOfType(UsesPermissionSdk23.class, true);
      if (usesPermissionSdk23 != null) {
        permissionName = usesPermissionSdk23.getName().getStringValue();
      }
    }

    if (permissionName == null) {
      return Collections.emptyList();
    }

    Map<String, List<String>> permissionsMap = getPermissionsMap(context);
    List<String> validPurposes = permissionsMap.get(permissionName);

    return validPurposes != null ? validPurposes : Collections.emptyList();
  }

  @Nullable
  @Override
  public String fromString(@Nullable String s, @NotNull ConvertContext context) { return s; }

  @Nullable
  @Override
  public String toString(@Nullable String s, @NotNull ConvertContext context) { return s; }

  @VisibleForTesting
  public static void clearCache() {
    purposeMapCache = new ConcurrentHashMap<>();
  }

  private static Map<String, List<String>> getPermissionsMap(ConvertContext context) {
    Module module = context.getModule();
    if (module == null) return Collections.emptyMap();

    AndroidPlatform platform = AndroidPlatforms.getInstance(module);
    if (platform == null) return Collections.emptyMap();

    IAndroidTarget target = platform.getTarget();

    String sdkHash = target.hashString();
    try {
      // Fetch permission info in a thread-safe manner by storing in cache if the file can be
      // parsed without issues.
      return purposeMapCache.computeIfAbsent(sdkHash, key -> {
        try {
          return parsePermissionsFile(target);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }
    catch (RuntimeException e) {
      LOG.error("Error reading permissions info from SDK", e);
      return Collections.emptyMap();
    }
  }

  private static Map<String, List<String>> parsePermissionsFile(IAndroidTarget target) throws Exception {
    Path platformDataPath = target.getPath(IAndroidTarget.PERMISSION_VERSIONS);
    File dataFile = new File(platformDataPath.toString());
    if (!dataFile.exists()) {
      return Collections.emptyMap();
    }

    Map<String, List<String>> mapBuilder = new HashMap<>();
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(dataFile);
    doc.getDocumentElement().normalize();
    NodeList permissionNodes = doc.getElementsByTagName(TAG_PERMISSION);
    for (int i = 0; i < permissionNodes.getLength(); i++) {
      Element permissionElement = (Element)permissionNodes.item(i);
      String permissionName = permissionElement.getAttribute(ATTR_NAME);
      if (permissionName.isEmpty()) {
        continue;
      }
      List<String> purposes = new ArrayList<>();
      NodeList purposeNodes = permissionElement.getElementsByTagName(TAG_VALID_PURPOSE);
      for (int j = 0; j < purposeNodes.getLength(); j++) {
        Element purposeElement = (Element)purposeNodes.item(j);
        String purposeName = purposeElement.getAttribute(ATTR_NAME);
        if (!purposeName.isEmpty()) {
          purposes.add(purposeName);
        }
      }
      if (!purposes.isEmpty()) {
        mapBuilder.put(permissionName, purposes);
      }
    }

    return mapBuilder;
  }
}
