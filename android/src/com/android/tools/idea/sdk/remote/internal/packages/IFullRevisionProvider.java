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

package com.android.tools.idea.sdk.remote.internal.packages;

import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.MajorRevision;


/**
 * Interface for packages that provide a {@link FullRevision},
 * which is a multi-part revision number (major.minor.micro) and an optional preview revision.
 * <p/>
 * This interface is a tag. It indicates that {@link com.android.tools.idea.sdk.remote.internal.packages.Package#getRevision()} returns a
 * {@link FullRevision} instead of a limited {@link MajorRevision}. <br/>
 * The preview version number is available via {@link com.android.tools.idea.sdk.remote.internal.packages.Package#getRevision()}.
 */
public interface IFullRevisionProvider {

  /**
   * Returns whether the given package represents the same item as the current package.
   * <p/>
   * Two packages are considered the same if they represent the same thing, except for the
   * revision number.
   *
   * @param pkg            The package to compare
   * @param comparePreview How to compare previews.
   * @return true if the items are the same.
   */
  boolean sameItemAs(Package pkg, PreviewComparison comparePreview);

}
