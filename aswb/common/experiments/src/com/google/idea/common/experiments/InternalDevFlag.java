/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.experiments;

import com.google.common.annotations.VisibleForTesting;

/**
 * Used to identify whether the current user is a blaze plugin developer.
 *
 * <p>Used for the purposes of dogfooding experimental features, or turning on additional logging.
 */
public final class InternalDevFlag {

  @VisibleForTesting
  static final DeveloperFlag disableInternalDevMarker =
      new DeveloperFlag("disable.internal.dev.marker");

  private static volatile boolean isInternalDev = false;

  /** Marks the current user as an internal plugin dev. */
  public static void markUserAsInternalDev(boolean isInternalDev) {
    InternalDevFlag.isInternalDev = isInternalDev;
  }

  /** Returns whether the current user is marked as an internal plugin dev. */
  public static boolean isInternalDev() {
    return isInternalDev && isInternalDevMarkerSupported();
  }

  /**
   * Returns whether the internal plugin dev marker is supported, <strong>regardless of whether the
   * current user is marked as a dev</strong>.
   */
  private static boolean isInternalDevMarkerSupported() {
    return !disableInternalDevMarker.getValue()
        && !ExperimentUsernameProvider.isUsernameOverridden();
  }
}
