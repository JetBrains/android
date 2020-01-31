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
package com.android.tools.idea.updater.configure;


import static com.android.SdkConstants.FD_NDK;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import org.jetbrains.annotations.NotNull;

/**
 * State of a row in {@link SdkUpdaterConfigurable}.
 */
class PackageNodeModel {

  enum SelectedState {
    NOT_INSTALLED,
    MIXED,
    INSTALLED
  }

  private final UpdatablePackage myPkg;
  private SelectedState myState;
  private final String myTitle;

  public PackageNodeModel(@NotNull UpdatablePackage pkg) {
    RepoPackage representative = pkg.getRepresentative();
    String name = representative.getDisplayName();
    String suffix = representative.getPath().substring(representative.getPath().lastIndexOf(RepoPackage.PATH_SEPARATOR) + 1);
    String shortRevision;
    try {
      shortRevision = Revision.parseRevision(suffix).toShortString();
    }
    catch (NumberFormatException ignore) {
      shortRevision = null;
    }
    if (representative.getDisplayName().endsWith(suffix) ||
        (shortRevision != null && representative.getDisplayName().endsWith(shortRevision))) {
      name = suffix;
    }

    myPkg = pkg;
    if (obsolete()) {
      name += " (Obsolete)";
    }
    myTitle = name;
  }

  @NotNull
  public UpdatablePackage getPkg() {
    return myPkg;
  }

  @Nullable  // Should only be null if it hasn't been initialized yet.
  public SelectedState getState() {
    return myState;
  }

  public void setState(@NotNull SelectedState state) {
    myState = state;
  }

  @NonNull
  public String getTitle() {
    return myTitle;
  }

  public boolean obsolete() {
    return myPkg.getRepresentative().obsolete() || myPkg.getPath().equals(FD_NDK);  // see bug 133519160
  }
}
