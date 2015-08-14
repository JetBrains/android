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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.archives.ArchiveReplacement;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents an archive that we want to install.
 * Note that the installer deals with archives whereas the user mostly sees packages
 * but as far as we are concerned for installation there's a 1-to-1 mapping.
 * <p/>
 * A new archive is always a remote archive that needs to be downloaded and then
 * installed. It can replace an existing local one. It can also depends on another
 * (new or local) archive, which means the dependent archive needs to be successfully
 * installed first. Finally this archive can also be a dependency for another one.
 * <p/>
 * The accepted and rejected flags are used by {@code SdkUpdaterChooserDialog} to follow
 * user choices. The installer should never install something that is not accepted.
 * <p/>
 * <em>Note</em>: There is currently no logic to support more than one level of
 * dependency, either here or in the {@code SdkUpdaterChooserDialog}, since we currently
 * have no need for it.
 *
 * @see ArchiveInfo#ArchiveInfo(Archive, Archive, ArchiveInfo[])
 */
public class ArchiveInfo extends ArchiveReplacement implements Comparable<ArchiveInfo> {

  private final ArchiveInfo[] mDependsOn;
  private final ArrayList<ArchiveInfo> mDependencyFor = new ArrayList<ArchiveInfo>();

  /**
   * Creates a new replacement where the {@code newArchive} will replace the
   * currently installed {@code replaced} archive.
   * When {@code newArchive} is not intended to replace anything (e.g. because
   * the user is installing a new package not present on her system yet), then
   * {@code replace} shall be null.
   *
   * @param newArchive A "new archive" to be installed. This is always an archive
   *                   that comes from a remote site. This <em>may</em> be null.
   * @param replaced   An optional local archive that the new one will replace.
   *                   Can be null if this archive does not replace anything.
   * @param dependsOn  An optional new or local dependency, that is an archive that
   *                   <em>this</em> archive depends upon. In other words, we can only install
   *                   this archive if the dependency has been successfully installed. It also
   *                   means we need to install the dependency first. Can be null or empty.
   *                   However it cannot contain nulls.
   */
  public ArchiveInfo(@Nullable Archive newArchive, @Nullable LocalPkgInfo replaced, @Nullable ArchiveInfo[] dependsOn) {
    super(newArchive, replaced);
    mDependsOn = dependsOn;
  }

  /**
   * Returns an optional new or local dependency, that is an archive that <em>this</em>
   * archive depends upon. In other words, we can only install this archive if the
   * dependency has been successfully installed. It also means we need to install the
   * dependency first.
   * <p/>
   * This array can be null or empty. It can't contain nulls though.
   */
  @Nullable
  public ArchiveInfo[] getDependsOn() {
    return mDependsOn;
  }

  /**
   * Adds an {@link ArchiveInfo} for which <em>this</em> package is a dependency.
   * This means the package added here depends on this package.
   */
  @NonNull
  public ArchiveInfo addDependencyFor(ArchiveInfo dependencyFor) {
    if (!mDependencyFor.contains(dependencyFor)) {
      mDependencyFor.add(dependencyFor);
    }

    return this;
  }

  /**
   * ArchiveInfos are compared using ther "new archive" ordering.
   *
   * @see Archive#compareTo(Archive)
   */
  @Override
  public int compareTo(ArchiveInfo rhs) {
    if (getNewArchive() != null && rhs != null) {
      return getNewArchive().compareTo(rhs.getNewArchive());
    }
    return 0;
  }
}
