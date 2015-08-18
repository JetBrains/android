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

import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;

/**
 * An {@link UpdatableExternalComponent} that corresponds to an
 * {@link IPkgDesc} for a local or remote package.
 */
public class UpdatablePackage implements UpdatableExternalComponent {
  private final IPkgDesc myPackage;

  public UpdatablePackage(IPkgDesc p) {
    myPackage = p;
  }

  @Override
  public IPkgDesc getKey() {
    return myPackage;
  }

  @Override
  public boolean isUpdateFor(UpdatableExternalComponent c) {
    if (c == null) {
      return false;
    }
    Object otherKey = c.getKey();
    if (!(otherKey instanceof IPkgDesc)) {
      return false;
    }
    // Ignore preview since we will only have created remote UpdatablePackages if previews were enabled
    return myPackage.isUpdateFor((IPkgDesc)otherKey, FullRevision.PreviewComparison.IGNORE);
  }

  @Override
  public String getName() {
    return myPackage.getListDescription();
  }

  @Override
  public String toString() {
    return getName();
  }
}
