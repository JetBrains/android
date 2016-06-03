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

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.google.common.base.Charsets.UTF_8;

public class GradleInstantRunContext implements InstantRunContext {
  private final String myApplicationId;
  private final AndroidFacet myFacet;
  private final AndroidGradleModel myModel;
  private BuildSelection myBuildChoice;

  public GradleInstantRunContext(@NotNull String applicationId, @NotNull AndroidFacet appFacet) {
    myApplicationId = applicationId;
    myFacet = appFacet;
    myModel = AndroidGradleModel.get(appFacet);
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
  public HashCode getManifestResourcesHash() {
    File manifest = findMergedManifestFile(myFacet);
    if (manifest != null) { // ensures exists too
      HashFunction hashFunction = Hashing.goodFastHash(32);
      final AppResourceRepository resources = AppResourceRepository.getAppResources(myFacet, true);
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
      }
      catch (IOException ignore) {
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

  /**
   * Looks up the merged manifest file for a given facet
   */
  @Nullable
  public static File findMergedManifestFile(@NotNull AndroidFacet facet) {
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model != null) {
      AndroidArtifact mainArtifact = model.getSelectedVariant().getMainArtifact();
      Collection<AndroidArtifactOutput> outputs = mainArtifact.getOutputs();
      for (AndroidArtifactOutput output : outputs) {
        // For now, use first manifest file that exists
        File manifest = output.getGeneratedManifest();
        if (manifest.exists()) {
          return manifest;
        }
      }
    }

    return null;
  }

  @Override
  public boolean usesMultipleProcesses() {
    // Note: Relying on the merged manifest implies that this will not work if a build has not already taken place.
    // But in this particular scenario (i.e. for instant run), we are ok with such a situation because:
    //      a) if there is no existing build, we are doing a full build anyway
    //      b) if there is an existing build, then we can examine the previous merged manifest
    //      c) if there is an existing build, and the manifest has since been changed, then a full build will be triggered anyway
    File manifest = findMergedManifestFile(myFacet);
    if (manifest == null || !manifest.exists()) {
      return false;
    }

    String xml;
    try {
      xml = Files.toString(manifest, UTF_8);
    }
    catch (IOException e) {
      InstantRunManager.LOG.warn("Error while reading merged manifest", e);
      return false;
    }

    return manifestSpecifiesMultiProcess(xml, InstantRunManager.ALLOWED_MULTI_PROCESSES);
  }

  @Nullable
  @Override
  public FileChangeListener.Changes getFileChangesAndReset() {
    return InstantRunManager.get(myFacet.getModule().getProject()).getChangesAndReset();
  }

  @NotNull
  @Override
  public List<String> getCustomBuildArguments() {
    if (myModel.isLibrary()) {
      return Collections.emptyList();
    }

    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(myFacet.getModule());
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
  static boolean manifestSpecifiesMultiProcess(@NotNull String manifest, @NotNull Set<String> allowedProcesses) {
    Matcher m = Pattern.compile("android:process\\s?=\\s?\"(.*)\"").matcher(manifest);
    while (m.find()) {
      String group = m.group(1);
      if (!allowedProcesses.contains(group)) {
        return true;
      }
    }

    return false;
  }
}
