/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

package com.android.tools.idea.rendering.tokens;

import com.android.tools.idea.rendering.BuildTargetReference;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;

/**
 * BazelBuildServices keeps references to the output JAR paths from the "Build & Refresh" Blaze build initiated from the composable preview
 * pane. The references are in a map keyed by these keys.
 *
 * @param project the path of the ASwB project
 * @param target  the target for the rule for the source file. Something like
 *                //experimental/users/user/java/com/google/hellogoogle3compose:lib (an android_library) for
 *                //experimental/users/user/java/com/google/hellogoogle3compose:MainActivity.kt (a Compose source file).
 */
record Key(Path project, Label target) {
  Key(BazelBuildTargetReference target) {
    this(getProject(target), toLabel(target));
  }

  private static Path getProject(BuildTargetReference target) {
    var path = target.getProject().getBasePath();
    assert path != null;

    return Path.of(path);
  }

  private static Label toLabel(BazelBuildTargetReference target) {
    var project = target.getProject();
    var path = target.getFileWorkspaceRelativePath();

    return Iterables.getOnlyElement(QuerySyncManager.getInstance(project).getCurrentSnapshot().orElseThrow().getTargetOwners(path));
  }
}
