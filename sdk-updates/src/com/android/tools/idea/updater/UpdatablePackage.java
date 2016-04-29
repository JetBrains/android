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
package com.android.tools.idea.updater;

import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;

/**
 * An {@link UpdatableExternalComponent} that corresponds to a local or remote {@link RepoPackage}.
 */
public class UpdatablePackage implements UpdatableExternalComponent {
  private final RepoPackage myPackage;

  public UpdatablePackage(RepoPackage p) {
    myPackage = p;
  }

  @Override
  public RepoPackage getKey() {
    return myPackage;
  }

  @Override
  public boolean isUpdateFor(UpdatableExternalComponent c) {
    if (c == null) {
      return false;
    }
    Object otherKey = c.getKey();
    if (!(otherKey instanceof RepoPackage)) {
      return false;
    }
    RepoPackage otherPackage = (RepoPackage)otherKey;
    // Ignore preview since we will only have created remote UpdatablePackages if previews were enabled
    return myPackage.getPath().equals(otherPackage.getPath()) &&
           myPackage.getVersion().compareTo(otherPackage.getVersion(),Revision.PreviewComparison.IGNORE) > 0;
  }

  @Override
  public String getName() {
    return myPackage.getDisplayName();
  }

  @Override
  public String toString() {
    return getName();
  }
}
