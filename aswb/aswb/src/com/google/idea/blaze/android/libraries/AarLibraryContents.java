/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.android.libraries;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import javax.annotation.Nullable;

/*  Provides BlazeArtifacts to unpack an Aar. */
@AutoValue
abstract class AarLibraryContents {
  static AarLibraryContents create(
      BlazeArtifact aar, @Nullable BlazeArtifact jar, ImmutableList<BlazeArtifact> srcJars) {
    return new AutoValue_AarLibraryContents(aar, jar, srcJars);
  }

  /* Provides BlazeArtifact of the .aar file that we need to fetch and copy locally */
  abstract BlazeArtifact aar();

  /**
   * Provides BlazeArtifact of the jar file that we need to fetch and copy locally. We do not use
   * jar in .aar file since it gives us freedom in the future to use an ijar or header jar instead,
   * which is more lightweight.
   *
   * <p>Return null if there's not jar for the aar file (e.g. the aar created by aspect which only
   * contains resource files).
   */
  @Nullable
  abstract BlazeArtifact jar();

  /** Returns the source jar specified in the srcjar attribute of aar_import. */
  abstract ImmutableList<BlazeArtifact> srcJars();
}
