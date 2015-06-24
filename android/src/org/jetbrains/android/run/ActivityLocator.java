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
package org.jetbrains.android.run;

import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public abstract class ActivityLocator {
  public static final class ActivityLocatorException extends Exception {
    public ActivityLocatorException(String message) {
      super(message);
    }

    public ActivityLocatorException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Validates whether the provided facet has the necessary activity for launch.
   *
   * NOTE: This is called before a build is performed, so for certain build systems, it may not be able
   * to perform a full validation, and an exception might be thrown by {@link #getQualifiedActivityName()}.
   */
  public abstract void validate(@NotNull AndroidFacet facet) throws ActivityLocatorException;

  /**
   * Returns the fully qualified launcher activity name.
   */
  @NotNull
  protected abstract String getQualifiedActivityName() throws ActivityLocatorException;
}
