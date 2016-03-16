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

package com.android.tools.idea.sdk.legacy.remote.internal.packages;

import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkRepoConstants;

/**
 * Interface used to decorate a {@link Package} that has a dependency
 * on a minimal API level, e.g. which XML has a <code>&lt;min-api-level&gt;</code> element.
 * <p/>
 * A package that has this dependency can only be installed if a platform with at least the
 * requested API level is present or installed at the same time.
 */
public interface IMinApiLevelDependency {

  /**
   * The value of {@link #getMinApiLevel()} when the {@link SdkRepoConstants#NODE_MIN_API_LEVEL}
   * was not specified in the XML source.
   */
  int MIN_API_LEVEL_NOT_SPECIFIED = 0;

  /**
   * Returns the minimal API level required by this package, if > 0,
   * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
   */
  int getMinApiLevel();
}
