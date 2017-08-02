/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceUrl;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.BuildSelection;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.google.common.base.Charsets.UTF_8;

public class GradleInstantRunContext implements InstantRunContext {
  private final String myApplicationId;
  private final AndroidFacet myFacet;
  private final AndroidModuleModel myModel;
  private BuildSelection myBuildChoice;

  public GradleInstantRunContext(@NotNull String applicationId, @NotNull AndroidFacet appFacet) {
    myApplicationId = applicationId;
    myFacet = appFacet;
    myModel = AndroidModuleModel.get(appFacet);
  }

  @Nullable
  @Override
  public InstantRunBuildInfo getInstantRunBuildInfo() {
    return InstantRunGradleUtils.getBuildInfo(myModel);
  }

  @Override
  public void setBuildSelection(@NotNull BuildSelection buildSelection) {
    myBuildChoice = buildSelection;
  }

  @Nullable
  @Override
  public BuildSelection getBuildSelection() {
    return myBuildChoice;
  }

  @NotNull
  @Override
  public String getApplicationId() {
    return myApplicationId;
  }

  @NotNull
  @Override
  public GradleVersion getGradlePluginVersion() {
    GradleVersion version = myModel.getModelVersion();
    return version == null ? new GradleVersion(0, 0, 0) : version;
  }

  @NotNull
  @Override
  public HashCode getManifestResourcesHash() {
    return getManifestResourcesHash(myFacet);
  }

  @VisibleForTesting
  static HashCode getManifestResourcesHash(@NotNull AndroidFacet facet) {
    Document manifest = MergedManifest.get(facet).getDocument();
    if (manifest == null || manifest.getDocumentElement() == null) {
      return HashCode.fromInt(0);
    }

    final Hasher hasher = Hashing.goodFastHash(32).newHasher();
    SortedSet<ResourceUrl> appResourceReferences = getAppResourceReferences(manifest.getDocumentElement());
    AppResourceRepository appResources = AppResourceRepository.getOrCreateInstance(facet);

    // read action needed when reading the values for app resources
    ApplicationManager.getApplication().runReadAction(() -> hashResources(appResourceReferences, appResources, hasher));

    return hasher.hash();
  }

  @VisibleForTesting
  static SortedSet<ResourceUrl> getAppResourceReferences(@NotNull Element element) {
    SortedSet<ResourceUrl> refs = new TreeSet<>(Comparator.comparing(ResourceUrl::toString));
    addAppResourceReferences(element, refs);
    return refs;
  }

  private static void addAppResourceReferences(@NotNull Element element, @NotNull Set<ResourceUrl> refs) {
    NamedNodeMap attributes = element.getAttributes();
    if (attributes != null) {
      for (int i = 0, n = attributes.getLength(); i < n; i++) {
        Node attribute = attributes.item(i);
        String value = attribute.getNodeValue();
        if (value.startsWith(PREFIX_RESOURCE_REF)) {
          ResourceUrl url = ResourceUrl.parse(value);
          if (url != null && !url.framework) {
            refs.add(url);
          }
        }
      }
    }

    NodeList children = element.getChildNodes();
    for (int i = 0, n = children.getLength(); i < n; i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        addAppResourceReferences((Element)child, refs);
      }
    }
  }

  private static void hashResources(@NotNull SortedSet<ResourceUrl> appResources,
                                    @NotNull AppResourceRepository resources,
                                    @NotNull Hasher hasher) {
    for (ResourceUrl url : appResources) {
      List<ResourceItem> items = resources.getResourceItem(url.type, url.name);
      if (items == null) {
        continue;
      }

      for (ResourceItem item : items) {
        ResourceValue resourceValue = item.getResourceValue(false);
        if (resourceValue != null) {
          String text = resourceValue.getValue();
          if (text != null) {
            if (ResourceHelper.isFileBasedResourceType(url.type)) {
              File f = new File(text);
              if (f.exists()) {
                try {
                  hasher.putBytes(Files.toByteArray(f));
                }
                catch (IOException ignore) {
                }
              }
            }
            else {
              hasher.putString(text, UTF_8);
            }
          }
        }
      }
    }
  }

  @Override
  public boolean usesMultipleProcesses() {
    Document manifest = MergedManifest.get(myFacet).getDocument();
    if (manifest == null) {
      return false;
    }

    // TODO: this needs to be fixed to search through the attributes
    return manifestSpecifiesMultiProcess(manifest.getDocumentElement(), InstantRunManager.ALLOWED_MULTI_PROCESSES);
  }

  @NotNull
  @Override
  public List<String> getCustomBuildArguments() {
    if (myModel.getAndroidProject().getProjectType() != PROJECT_TYPE_APP) {
      return Collections.emptyList();
    }

    GradleFacet facet = GradleFacet.getInstance(myFacet.getModule());
    if (facet == null) {
      Logger.getInstance(GradleInstantRunContext.class).warn("Unable to obtain gradle facet for module " + myFacet.getModule().getName());
      return Collections.emptyList();
    }

    // restrict the variants that get configured
    return ImmutableList.of(AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_RESTRICT_VARIANT_NAME,
                                                                        myModel.getSelectedVariant().getName()),
                            AndroidGradleSettings.createProjectProperty(AndroidProject.PROPERTY_RESTRICT_VARIANT_PROJECT,
                                                                        facet.getConfiguration().GRADLE_PROJECT_PATH));
  }

  /**
   * Returns whether the given manifest file uses multiple processes other than the specified ones.
   */
  static boolean manifestSpecifiesMultiProcess(@Nullable Element element, @NotNull Set<String> allowedProcesses) {
    if (element == null) {
      return false;
    }

    NodeList children = element.getChildNodes();
    for (int i = 0, n = children.getLength(); i < n; i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        if (manifestSpecifiesMultiProcess((Element)child, allowedProcesses)) {
          return true;
        }
      }
    }

    String process = element.getAttributeNS(ANDROID_URI, "process");
    if (!process.isEmpty() && !allowedProcesses.contains(process)) {
      return true;
    }

    return false;
  }
}
