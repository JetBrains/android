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

import com.android.sdklib.repository.RepoConstants;

/**
 * Interface used to decorate a {@link Package} that has a dependency
 * on a specific API level, e.g. which XML has a {@code <api-level>} element.
 * <p/>
 * For example an add-on package requires a platform with an exact API level to be installed
 * at the same time.
 * This is not the same as {@link IMinApiLevelDependency} which requests that a platform with at
 * least the requested API level be present or installed at the same time.
 * <p/>
 * Such package requires the {@code <api-level>} element. It is not an optional
 * property, however it can be invalid.
 */
public interface IExactApiLevelDependency {

  /**
   * The value of {@link #getExactApiLevel()} when the {@link RepoConstants#NODE_API_LEVEL}
   * was not specified in the XML source.
   */
  int API_LEVEL_INVALID = 0;

  /**
   * Returns the exact API level required by this package, if > 0,
   * or {@link #API_LEVEL_INVALID} if the value was missing.
   * <p/>
   * This attribute is mandatory and should not be normally missing.
   * It can only happen when dealing with an invalid repository XML.
   */
  int getExactApiLevel();
}
