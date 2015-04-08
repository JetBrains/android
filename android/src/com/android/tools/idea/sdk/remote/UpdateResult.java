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

package com.android.tools.idea.sdk.remote;

import com.android.annotations.NonNull;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.google.common.collect.Sets;

import java.util.Set;


/**
 * Results from {@link Update#computeUpdates(LocalPkgInfo[], com.google.common.collect.Multimap)}.
 */
public final class UpdateResult {
  private final Set<UpdatablePkgInfo> mUpdatedPkgs = Sets.newTreeSet();
  private final Set<RemotePkgInfo> mNewPkgs = Sets.newTreeSet();
  private final long mTimestampMs;

  public UpdateResult() {
    mTimestampMs = System.currentTimeMillis();
  }

  /**
   * Returns the timestamp (in {@link System#currentTimeMillis()} time) when this object was created.
   */
  public long getTimestampMs() {
    return mTimestampMs;
  }

  /**
   * Returns the set of packages that have local updates available.
   * Use {@link LocalPkgInfo#getUpdate()} to retrieve the computed updated candidate.
   *
   * @return A non-null, possibly empty list of update candidates.
   */
  @NonNull
  public Set<UpdatablePkgInfo> getUpdatedPkgs() {
    return mUpdatedPkgs;
  }

  /**
   * Returns the set of new remote packages that are not locally present
   * and that the user could install.
   *
   * @return A non-null, possibly empty list of new install candidates.
   */
  @NonNull
  public Set<RemotePkgInfo> getNewPkgs() {
    return mNewPkgs;
  }

  /**
   * Add a package to the set of packages with available updates.
   *
   * @param pkgInfo The {@link LocalPkgInfo} which has an available update.
   */
  void addUpdatedPkgs(@NonNull UpdatablePkgInfo pkgInfo) {
    mUpdatedPkgs.add(pkgInfo);
  }

  /**
   * Add a package to the set of new remote packages that are not locally present
   * and that the user could install.
   *
   * @param pkgInfo The {@link RemotePkgInfo} which has an available update.
   */
  void addNewPkgs(@NonNull RemotePkgInfo pkgInfo) {
    mNewPkgs.add(pkgInfo);
  }
}
