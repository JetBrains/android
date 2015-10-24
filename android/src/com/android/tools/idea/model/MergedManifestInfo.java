/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.resources.ScreenSize;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MergedManifestInfo extends ManifestInfo {
  @NotNull private final Module myModule;
  @NotNull private final AndroidFacet myAndroidFacet;

  private Set<VirtualFile> myManifestFiles = Sets.newConcurrentHashSet();
  private final AtomicLong myLastChecked = new AtomicLong(0);
  private AtomicReference<List<Manifest>> myManifestsRef = new AtomicReference<List<Manifest>>(Collections.<Manifest>emptyList());

  private static final String MERGING_UNSUPPORTED =
    "This class does not perform a proper manifest merge algorithm, and so the requested information "
    + "isn't available. Consider querying the Gradle model or obtain the information from the primary manifest.";

  MergedManifestInfo(@NotNull AndroidFacet facet) {
    myModule = facet.getModule();
    myAndroidFacet = facet;
  }

  @NotNull
  private static Set<VirtualFile> getAllManifests(@NotNull AndroidFacet facet) {
    Set<VirtualFile> allManifests = Sets.newHashSet();
    allManifests.addAll(IdeaSourceProvider.getManifestFiles(facet));

    for (AndroidFacet dependency : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      allManifests.addAll(IdeaSourceProvider.getManifestFiles(dependency));
    }

    return allManifests;
  }

  @Override
  public void clear() {
    myLastChecked.set(0);
  }

  @Nullable
  @Override
  public String getPackage() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @NotNull
  @Override
  public Map<String, ActivityAttributes> getActivityAttributesMap() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @Nullable
  @Override
  public ActivityAttributes getActivityAttributes(@NotNull String activity) {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @Nullable
  @Override
  public String getManifestTheme() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @NotNull
  @Override
  public String getDefaultTheme(@Nullable IAndroidTarget renderingTarget, @Nullable ScreenSize screenSize, @Nullable Device device) {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @Nullable
  @Override
  public String getApplicationIcon() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @Nullable
  @Override
  public String getApplicationLabel() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @Override
  public boolean isRtlSupported() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @Nullable
  @Override
  public Boolean getApplicationDebuggable() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    throw new UnsupportedOperationException(MERGING_UNSUPPORTED);
  }

  @NotNull
  @Override
  protected List<Manifest> getManifests() {
    sync();
    return myManifestsRef.get();
  }

  private synchronized void sync() {
    boolean needsRefresh = false;

    // needs a refresh if the list of manifests changed due to a variant change or a sync with new build script
    final Set<VirtualFile> currentManifests = getAllManifests(myAndroidFacet);
    if (!currentManifests.equals(myManifestFiles)) {
      needsRefresh = true;
    }

    // needs a refresh if one of the manifests has a newer timestamp
    long maxLastModified = getMaxLastModified();
    if (myLastChecked.get() < maxLastModified) {
      needsRefresh = true;
    }

    if (needsRefresh) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          syncWithReadPermission(currentManifests);
        }
      });
    }

    // Set the cache state after syncWithReadPermission() is complete, as this entire process could be called from
    // a cancellable process, and if we had set this before, then we'd end up in an inconsistent state

    if (!currentManifests.equals(myManifestFiles)) {
      myManifestFiles = currentManifests;
      myLastChecked.set(0);
    }

    if (myLastChecked.get() < maxLastModified) {
      myLastChecked.set(maxLastModified);
    }
  }

  private long getMaxLastModified() {
    long max = 0;
    for (VirtualFile f : myManifestFiles) {
      if (f.getModificationStamp() > max) {
        max = f.getModificationStamp();
      }
    }

    return max;
  }

  private void syncWithReadPermission(Set<VirtualFile> files) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<Manifest> manifests = Lists.newArrayListWithExpectedSize(files.size());
    for (VirtualFile f : files) {
      PsiFile psiFile = PsiManager.getInstance(myModule.getProject()).findFile(f);
      if (psiFile instanceof XmlFile) {
        Manifest m = AndroidUtils.loadDomElementWithReadPermission(myModule.getProject(), (XmlFile)psiFile, Manifest.class);
        // If the file reported as a manifest is invalid, m will be null.
        if (m != null) {
          manifests.add(m);
        }
      }
    }

    myManifestsRef.set(manifests);
  }
}
