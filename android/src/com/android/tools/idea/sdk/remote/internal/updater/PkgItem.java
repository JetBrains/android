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

package com.android.tools.idea.sdk.remote.internal.updater;

import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.packages.IAndroidVersionProvider;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.google.common.base.Objects;

/**
 * A {@link PkgItem} represents one main {@link Package} combined with its state
 * and an optional update package.
 * <p/>
 * The main package is final and cannot change since it's what "defines" this PkgItem.
 * The state or update package can change later.
 */
public class PkgItem implements Comparable<PkgItem> {
  private final PkgState mState;
  private final LocalPkgInfo mMainPkg;
  private RemotePkgInfo mUpdatePkg;
  private boolean mChecked;

  /**
   * The state of the a given {@link PkgItem}, that is the relationship between
   * a given remote package and the local repository.
   */
  public enum PkgState {
    // Implementation detail: if this is changed then PackageDiffLogic#STATES
    // and PackageDiffLogic#processSource() need to be changed accordingly.

    /**
     * Package is locally installed and may or may not have an update.
     */
    INSTALLED,

    /**
     * There's a new package available on the remote site that isn't installed locally.
     */
    NEW
  }

  /**
   * Create a new {@link PkgItem} for this main package.
   * The main package is final and cannot change since it's what "defines" this PkgItem.
   * The state or update package can change later.
   */
  public PkgItem(LocalPkgInfo mainPkg, PkgState state) {
    mMainPkg = mainPkg;
    mState = state;
    assert mMainPkg != null;
  }

  public boolean isObsolete() {
    return mMainPkg.getDesc().isObsolete();
  }

  public boolean isChecked() {
    return mChecked;
  }

  public void setChecked(boolean checked) {
    mChecked = checked;
  }

  public RemotePkgInfo getUpdatePkg() {
    return mUpdatePkg;
  }

  public boolean hasUpdatePkg() {
    return mUpdatePkg != null;
  }

  public String getName() {
    return mMainPkg.getListDescription();
  }

  public FullRevision getRevision() {
    return mMainPkg.getDesc().getFullRevision();
  }

  public LocalPkgInfo getMainPackage() {
    return mMainPkg;
  }

  public PkgState getState() {
    return mState;
  }

  @Nullable
  public SdkSource getSource() {
    return mUpdatePkg == null ? null : mUpdatePkg.getParentSource();
  }

  @Nullable
  public AndroidVersion getAndroidVersion() {
    return mMainPkg instanceof IAndroidVersionProvider ? ((IAndroidVersionProvider)mMainPkg).getAndroidVersion() : null;
  }

  @Nullable
  public Archive[] getArchives() {
    return mUpdatePkg == null ? null : mUpdatePkg.getArchives();
  }

  @Override
  public int compareTo(PkgItem pkg) {
    return getMainPackage().compareTo(pkg.getMainPackage());
  }

  /**
   * Equality is defined as {@link #isSameItemAs(PkgItem)}: state, main package
   * and update package must be the similar.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PkgItem)) {
      return false;
    }
    PkgItem other = (PkgItem)obj;
    return mMainPkg.equals(other.mMainPkg)
      && Objects.equal(mUpdatePkg, other.mUpdatePkg)
      && mState.equals(other.mState);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mState == null) ? 0 : mState.hashCode());
    result = prime * result + ((mMainPkg == null) ? 0 : mMainPkg.hashCode());
    result = prime * result + ((mUpdatePkg == null) ? 0 : mUpdatePkg.hashCode());
    return result;
  }

  /**
   * Check whether the 'pkg' argument is an update for this package.
   * If it is, record it as an updating package.
   * If there's already an updating package, only keep the most recent update.
   * Returns true if it is update (even if there was already an update and this
   * ended up not being the most recent), false if incompatible or not an update.
   * <p/>
   * This should only be used for installed packages.
   */
  public boolean mergeUpdate(RemotePkgInfo pkg) {
    if (mUpdatePkg == pkg) {
      return true;
    }
    if (pkg.canUpdate(mMainPkg) == RemotePkgInfo.UpdateInfo.UPDATE) {
      if (mUpdatePkg == null) {
        mUpdatePkg = pkg;
      }
      return true;
    }

    return false;
  }

  public void removeUpdate() {
    mUpdatePkg = null;
  }

  /**
   * Returns a string representation of this item, useful when debugging.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('<');

    if (mChecked) {
      sb.append(" * "); //$NON-NLS-1$
    }

    sb.append(mState.toString());

    if (mMainPkg != null) {
      sb.append(", pkg:"); //$NON-NLS-1$
      sb.append(mMainPkg.toString());
    }

    if (mUpdatePkg != null) {
      sb.append(", updated by:"); //$NON-NLS-1$
      sb.append(mUpdatePkg.toString());
    }

    sb.append('>');
    return sb.toString();
  }
}
