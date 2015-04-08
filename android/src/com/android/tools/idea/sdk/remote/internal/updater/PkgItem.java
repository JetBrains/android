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
import com.android.sdklib.repository.IDescription;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.packages.IAndroidVersionProvider;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.Package.UpdateInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;

/**
 * A {@link com.android.tools.idea.sdk.remote.internal.updater.PkgItem} represents one main {@link Package} combined with its state
 * and an optional update package.
 * <p/>
 * The main package is final and cannot change since it's what "defines" this PkgItem.
 * The state or update package can change later.
 */
public class PkgItem implements Comparable<com.android.tools.idea.sdk.remote.internal.updater.PkgItem> {
  private final PkgState mState;
  private final Package mMainPkg;
  private Package mUpdatePkg;
  private boolean mChecked;

  /**
   * The state of the a given {@link com.android.tools.idea.sdk.remote.internal.updater.PkgItem}, that is the relationship between
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
   * Create a new {@link com.android.tools.idea.sdk.remote.internal.updater.PkgItem} for this main package.
   * The main package is final and cannot change since it's what "defines" this PkgItem.
   * The state or update package can change later.
   */
  public PkgItem(Package mainPkg, PkgState state) {
    mMainPkg = mainPkg;
    mState = state;
    assert mMainPkg != null;
  }

  public boolean isObsolete() {
    return mMainPkg.isObsolete();
  }

  public boolean isChecked() {
    return mChecked;
  }

  public void setChecked(boolean checked) {
    mChecked = checked;
  }

  public Package getUpdatePkg() {
    return mUpdatePkg;
  }

  public boolean hasUpdatePkg() {
    return mUpdatePkg != null;
  }

  public String getName() {
    return mMainPkg.getListDescription();
  }

  public FullRevision getRevision() {
    return mMainPkg.getRevision();
  }

  /**
   * @deprecated Use {@link #getMainPackage()} with the {@link IDescription} interface instead.
   */
  @Deprecated
  public String getDescription() {
    return mMainPkg.getLongDescription();
  }

  public Package getMainPackage() {
    return mMainPkg;
  }

  public PkgState getState() {
    return mState;
  }

  public SdkSource getSource() {
    return mMainPkg.getParentSource();
  }

  @Nullable
  public AndroidVersion getAndroidVersion() {
    return mMainPkg instanceof IAndroidVersionProvider ? ((IAndroidVersionProvider)mMainPkg).getAndroidVersion() : null;
  }

  public Archive[] getArchives() {
    return mMainPkg.getArchives();
  }

  @Override
  public int compareTo(com.android.tools.idea.sdk.remote.internal.updater.PkgItem pkg) {
    return getMainPackage().compareTo(pkg.getMainPackage());
  }

  /**
   * Returns true if this package or its updating packages contains
   * the exact given archive.
   * Important: This compares object references, not object equality.
   */
  public boolean hasArchive(Archive archive) {
    if (mMainPkg.hasArchive(archive)) {
      return true;
    }
    if (mUpdatePkg != null && mUpdatePkg.hasArchive(archive)) {
      return true;
    }
    return false;
  }

  /**
   * Returns true if the main package has at least one archive
   * compatible with the current platform.
   */
  public boolean hasCompatibleArchive() {
    return mMainPkg.hasCompatibleArchive();
  }

  /**
   * Checks whether the main packages are of the same type and are
   * not an update of each other and have the same revision number.
   */
  public boolean isSameMainPackageAs(Package pkg) {
    if (mMainPkg.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE) {
      // package revision numbers must match
      return mMainPkg.getRevision().equals(pkg.getRevision());
    }
    return false;
  }

  /**
   * Checks whether the update packages are of the same type and are
   * not an update of each other and have the same revision numbers.
   */
  public boolean isSameUpdatePackageAs(Package pkg) {
    if (mUpdatePkg != null && mUpdatePkg.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE) {
      // package revision numbers must match
      return mUpdatePkg.getRevision().equals(pkg.getRevision());
    }
    return false;
  }

  /**
   * Checks whether too {@link com.android.tools.idea.sdk.remote.internal.updater.PkgItem} are the same.
   * This checks both items have the same state, both main package are similar
   * and that they have the same updating packages.
   */
  public boolean isSameItemAs(com.android.tools.idea.sdk.remote.internal.updater.PkgItem item) {
    if (this == item) {
      return true;
    }
    boolean same = this.mState == item.mState;
    if (same) {
      same = isSameMainPackageAs(item.getMainPackage());
    }

    if (same) {
      // check updating packages are the same
      Package p1 = this.mUpdatePkg;
      Package p2 = item.getUpdatePkg();
      same = (p1 == p2) || (p1 == null && p2 == null) || (p1 != null && p2 != null);

      if (same && p1 != null) {
        same = p1.canBeUpdatedBy(p2) == UpdateInfo.NOT_UPDATE;
      }
    }

    return same;
  }

  /**
   * Equality is defined as {@link #isSameItemAs(com.android.tools.idea.sdk.remote.internal.updater.PkgItem)}: state, main package
   * and update package must be the similar.
   */
  @Override
  public boolean equals(Object obj) {
    return (obj instanceof com.android.tools.idea.sdk.remote.internal.updater.PkgItem) &&
           this.isSameItemAs((com.android.tools.idea.sdk.remote.internal.updater.PkgItem)obj);
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
  public boolean mergeUpdate(Package pkg) {
    if (mUpdatePkg == pkg) {
      return true;
    }
    if (mMainPkg.canBeUpdatedBy(pkg) == UpdateInfo.UPDATE) {
      if (mUpdatePkg == null) {
        mUpdatePkg = pkg;
      }
      else if (mUpdatePkg.canBeUpdatedBy(pkg) == UpdateInfo.UPDATE) {
        // If we have more than one, keep only the most recent update
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
