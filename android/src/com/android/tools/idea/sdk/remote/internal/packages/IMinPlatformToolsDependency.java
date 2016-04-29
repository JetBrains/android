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

import com.android.repository.Revision;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;

/**
 * Interface used to decorate a {@link Package} that has a dependency
 * on a minimal platform-tools revision, e.g. which XML has a
 * <code>&lt;min-platform-tools-rev&gt;</code> element.
 * <p/>
 * A package that has this dependency can only be installed if the requested platform-tools
 * revision is present or installed at the same time.
 */
public interface IMinPlatformToolsDependency {

  /**
   * The value of {@link #getMinPlatformToolsRevision()} when the
   * {@link SdkRepoConstants#NODE_MIN_PLATFORM_TOOLS_REV} was not specified in the XML source.
   * Since this is a required attribute in the XML schema, it can only happen when dealing
   * with an invalid repository XML.
   */
  Revision MIN_PLATFORM_TOOLS_REV_INVALID = new Revision(Revision.MISSING_MAJOR_REV);

  /**
   * The minimal revision of the tools package required by this package if > 0,
   * or {@link #MIN_PLATFORM_TOOLS_REV_INVALID} if the value was missing.
   * <p/>
   * This attribute is mandatory and should not be normally missing.
   * It can only happen when dealing with an invalid repository XML.
   */
  Revision getMinPlatformToolsRevision();

}
