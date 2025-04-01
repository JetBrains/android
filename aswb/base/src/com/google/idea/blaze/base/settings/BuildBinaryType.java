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
package com.google.idea.blaze.base.settings;

/** The kind of the build system's binary */
public enum BuildBinaryType {
  NONE(false, false),
  BLAZE(false, false),
  BAZEL(false, true),
  RABBIT(true, false), // rabbit CLI
  RABBIT_API(true, false), // rabbit via RPCs
  BLAZE_CUSTOM(false, false);

  /** Whether the blaze invocations are run remotely. */
  public final boolean isRemote;

  public final boolean needsAndroidHome;

  BuildBinaryType(boolean isRemote, boolean needsAndroidHome) {
    this.isRemote = isRemote;
    this.needsAndroidHome = needsAndroidHome;
  }
}
