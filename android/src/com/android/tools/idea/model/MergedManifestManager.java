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
package com.android.tools.idea.model;

import com.android.annotations.concurrency.Slow;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Key;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MergedManifestManager {
  /**
   * Interval in milliseconds under which the {@link MergedManifestManager} will not check if the actual
   * XML file has been updated before issuing a full refresh. This avoids multiple fast calls to end up
   * doing a content check on all the manifests of the project.
   */
  private static final long REFRESH_CHECK_INTERVAL_MS = 50;
  private static final Key<MergedManifestSnapshot> KEY = Key.create("manifest.merger.by.module");

  /**
   * Returns the {@link MergedManifestSnapshot} for the given {@link Module}.
   *
   * @param module the android module
   * @return a {@link MergedManifestSnapshot} for the given module, never null
   */
  @Slow
  @NotNull
  public static MergedManifestSnapshot getSnapshot(@NotNull Module module, boolean forceRefresh) {
    if (module.isDisposed()) {
      return MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(module);
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Attempt to obtain manifest info from a non Android module: " + module.getName();

    return getSnapshot(facet, forceRefresh);
  }

  /**
   * Returns the {@link MergedManifestSnapshot} for the given {@link Module}.
   *
   * @param module the android module
   * @return a {@link MergedManifestSnapshot} for the given module, never null
   */
  @NotNull
  public static MergedManifestSnapshot getSnapshot(@NotNull Module module) {
    return getSnapshot(module, false);
  }

  /**
   * If {@link MergedManifestInfo} is out-of-date or null, returns a new {@link MergedManifestSnapshot} loaded from disk.
   * Returns null otherwise.
   *
   * @param facet the {@link AndroidFacet}
   * @param mergedManifestInfo the source {@link MergedManifestInfo}
   * @param forceLoad if true, the snapshot will be returned even if the mergedManifestInfo is up-to-date
   */
  @VisibleForTesting
  @Slow
  @Nullable
  static MergedManifestSnapshot readSnapshotFromDisk(@NotNull AndroidFacet facet,
                                                     @Nullable MergedManifestInfo mergedManifestInfo,
                                                     boolean forceLoad) {
    return ReadAction.compute(() -> {
      if (!facet.isDisposed() && (forceLoad || mergedManifestInfo == null || !mergedManifestInfo.isUpToDate())) {
        return MergedManifestSnapshotFactory.createMergedManifestSnapshot(facet, MergedManifestInfo.create(facet));
      }
      return null;
    });
  }

  /**
   * Returns the most up-to-date version of the MergedManifestSnapshot if available. If the passed version
   * is up-to-date or we can not retrieve a newer version, it will return the same instance.
   */
  @Slow
  @NotNull
  private static MergedManifestSnapshot getFreshSnapshot(@NotNull AndroidFacet facet, @NotNull MergedManifestSnapshot snapshot) {
    long creationTimestamp = snapshot.getCreationTimestamp();
    if (Clock.getTime() - creationTimestamp > REFRESH_CHECK_INTERVAL_MS) {
      // Check if the source file needs a refresh, if it does, then we need to re-issue the snapshot
      MergedManifestInfo mergedManifestInfo = snapshot.getMergedManifestInfo();
      if (mergedManifestInfo != null) {
        MergedManifestSnapshot newManifestSnapshot = readSnapshotFromDisk(facet, mergedManifestInfo, false);

        if (newManifestSnapshot != null) {
          return newManifestSnapshot;
        }
      }
      // We could not retrieve a new merged manifest. Return the old cached version
    }

    return snapshot;
  }

  /**
   * Returns the cached snapshot for the given {@link AndroidFacet} if available. Use this method if
   * you do not care about the freshnesss of the merged manifest and you want to avoid extensive operations.
   * This method will not block.
   */
  @Nullable
  public static MergedManifestSnapshot getCachedSnapshot(@NotNull AndroidFacet facet) {
    return facet.getModule().getUserData(KEY);
  }

  /**
   * Returns the {@link MergedManifestSnapshot} for the given {@link AndroidFacet}.
   *
   * @param facet the Android facet associated with a module.
   * @return a {@link MergedManifestSnapshot} for the given module
   */
  @Slow
  @NotNull
  public static MergedManifestSnapshot getSnapshot(@NotNull AndroidFacet facet, boolean forceRefresh) {
    Module module = facet.getModule();
    if (module.isDisposed()) {
      return MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(module);
    }

    MergedManifestSnapshot cachedManifest = getCachedSnapshot(facet);
    if (forceRefresh || cachedManifest == null) {
      MergedManifestInfo file = cachedManifest != null ? cachedManifest.getMergedManifestInfo() : null;
      cachedManifest = readSnapshotFromDisk(facet, file, forceRefresh);
      if (cachedManifest != null) {
        module.putUserData(KEY, cachedManifest);
      }
    }
    else {
      // We have an existing snapshot, check if we can get a newer version.
      cachedManifest = getFreshSnapshot(facet, cachedManifest);
      module.putUserData(KEY, cachedManifest);
    }

    return cachedManifest != null ? cachedManifest : MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(module);
  }

  /**
   * Returns the {@link MergedManifestSnapshot} for the given {@link AndroidFacet}.
   *
   * @param facet the Android facet associated with a module.
   * @return a {@link MergedManifestSnapshot} for the given module
   */
  @Slow
  @NotNull
  public static MergedManifestSnapshot getSnapshot(@NotNull AndroidFacet facet) {
    return getSnapshot(facet, false);
  }
}
