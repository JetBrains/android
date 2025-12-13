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

/** The kind of sync. */
public enum SyncMode {
  // DO NOT REORDER: ordinal is used to merge sync modes from multiple builds
  /** Happens on startup, restores in-memory state */
  STARTUP(/* involvesBlazeBuild= */ false),
  /** A partial sync, without any blaze build (i.e. updates directories / in-memory state only) */
  NO_BUILD(/* involvesBlazeBuild= */ false),
  /** Partial / working set sync */
  PARTIAL(/* involvesBlazeBuild= */ true),
  /** This is the standard incremental sync */
  INCREMENTAL(/* involvesBlazeBuild= */ true),
  /** Full sync, can invalidate/redo work that an incremental sync does not */
  FULL(/* involvesBlazeBuild= */ true);

  private final boolean involvesBlazeBuild;

  SyncMode(boolean involvesBlazeBuild) {
    this.involvesBlazeBuild = involvesBlazeBuild;
  }

  public boolean involvesBlazeBuild() {
    return involvesBlazeBuild;
  }
}
