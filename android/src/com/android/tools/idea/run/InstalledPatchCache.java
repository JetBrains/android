/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.utils.XmlUtils;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.google.common.base.Charsets.UTF_8;

public class InstalledPatchCache implements Disposable {
  private final DeviceStateCache<PatchState> myCache;

  public InstalledPatchCache() {
    myCache = new DeviceStateCache<PatchState>(this);
  }

  public long getInstalledManifestTimestamp(@NotNull IDevice device, @NotNull String pkgName) {
    PatchState state = getState(device, pkgName, false);
    return state == null ? 0 : state.manifestModified;
  }

  public void setInstalledManifestTimestamp(@NotNull IDevice device, @NotNull String pkgName, long timestamp) {
    getState(device, pkgName, true).manifestModified = timestamp;
  }

  @Nullable
  public HashCode getInstalledManifestResourcesHash(@NotNull IDevice device, @NotNull String pkgName) {
    PatchState state = getState(device, pkgName, false);
    return state == null ? null : state.manifestResources;
  }

  public void setInstalledManifestResourcesHash(@NotNull IDevice device, @NotNull String pkgName, HashCode hash) {
    getState(device, pkgName, true).manifestResources = hash;
  }

  @Contract("!null, !null, true -> !null")
  @Nullable
  private PatchState getState(@NotNull IDevice device, @NotNull String pkgName, boolean create) {
    PatchState state = myCache.get(device, pkgName);
    if (state == null && create) {
      state = new PatchState();
      myCache.put(device, pkgName, state);
    }
    return state;
  }

  /**
   * Computes a hashcode which encapsulates the set of resources referenced from the
   * merged manifest along with the values of those resources
   *
   * @param facet the app module whose merged manifest we're analyzing
   * @return a hashcode
   */
  @NotNull
  public static HashCode computeManifestResources(@NotNull AndroidFacet facet) {
    File manifest = InstantRunManager.findMergedManifestFile(facet);
    if (manifest != null) { // ensures exists too
      HashFunction hashFunction = Hashing.goodFastHash(32);
      final AppResourceRepository resources = AppResourceRepository.getAppResources(facet, true);
      final Hasher hasher = hashFunction.newHasher();

      // Read XML for manifest file
      // Look through resource references, and for each one look up the app resource repository, and for each one,
      // read the value and hash them.
      try {
        String xml = Files.toString(manifest, UTF_8);
        // Hack: turns out we *sometimes* see the injected bootstrap application,
        // and sometimes we don't. We Don't want this to be part of the checksum.
        // This should go away when we do our own merged manifest model (or when
        // the Gradle plugin's bootstrap application injection no longer handles
        // it this way.)
        // TODO: Remove when 2.0-alpha4 or later is fixed to not do this anymore.
        xml = xml.replace("        android:name=\"com.android.tools.fd.runtime.BootstrapApplication\"\n", "");
        hasher.putString(xml, UTF_8);
        final Document document = XmlUtils.parseDocumentSilently(xml, true);
        if (document != null && document.getDocumentElement() != null) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              hashResources(document.getDocumentElement(), hasher, resources);
            }
          });
        }
      } catch (IOException ignore) {
      }

      return hasher.hash();
    }

    return HashCode.fromInt(0);
  }

  private static void hashResources(Element element, Hasher hasher, AppResourceRepository resources) {
    NamedNodeMap attributes = element.getAttributes();
    for (int i = 0, n = attributes.getLength(); i < n; i++) {
      Node attribute = attributes.item(i);
      String value = attribute.getNodeValue();
      if (value.startsWith(PREFIX_RESOURCE_REF)) {
        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && !url.framework) {
          List<ResourceItem> items = resources.getResourceItem(url.type, url.name);
          if (items != null) {
            for (ResourceItem item : items) {
              ResourceValue resourceValue = item.getResourceValue(false);
              if (resourceValue != null) {
                String text = resourceValue.getValue();
                if (text != null) {
                  hasher.putString(text, UTF_8);
                }
              }
            }
          }
        }
      }
    }

    NodeList children = element.getChildNodes();
    for (int i = 0, n = children.getLength(); i < n; i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        hashResources((Element)child, hasher, resources);
      }
    }
  }

  @Override
  public void dispose() {
  }

  private static class PatchState {
    public long manifestModified;
    @Nullable public HashCode manifestResources;
  }
}
