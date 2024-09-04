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
package com.google.idea.blaze.base.sync;

/** Result of the sync operation */
public enum SyncResult {
  /** Full success */
  SUCCESS(true),
  /**
   * The user has errors in their BUILD files, compilation errors, or a one of the shards fails to
   * complete
   */
  PARTIAL_SUCCESS(true),
  /** The user cancelled */
  CANCELLED(false),
  /** Failure -- sync could not complete */
  FAILURE(false);

  private final boolean success;

  SyncResult(boolean success) {
    this.success = success;
  }

  public boolean successful() {
    return success;
  }

  public static SyncResult combine(SyncResult first, SyncResult second) {
    if (first == CANCELLED || second == CANCELLED) {
      return CANCELLED;
    }
    if (first == FAILURE || second == FAILURE) {
      return FAILURE;
    }
    return first == PARTIAL_SUCCESS || second == PARTIAL_SUCCESS ? PARTIAL_SUCCESS : SUCCESS;
  }
}
