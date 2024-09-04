/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Utility for replacing the effective workspace root in build output with the real project
 * workspace root.
 *
 * <p>An effective workspace root is the root that we run certain build commands in, but it is not
 * the location of the source that the users should be viewing/editing: it's a read-only snapshot of
 * that source at a point in time.
 *
 * <p>To prevent the IDE from opening these read-only sources, we replace the effective workspace
 * root in the build output with the real project workspace root.
 */
public class WorkspaceRootReplacement {

  private WorkspaceRootReplacement() {}

  public static Function<String, String> create(Path workspaceRoot, BlazeCommand command) {
    return command
        .getEffectiveWorkspaceRoot()
        .map(
            effectiveRoot ->
                (Function<String, String>)
                    stderr -> stderr.replace(effectiveRoot.toString(), workspaceRoot.toString()))
        .orElse(Function.identity());
  }
}
