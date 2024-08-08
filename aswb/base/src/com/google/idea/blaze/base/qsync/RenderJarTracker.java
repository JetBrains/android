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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A service responsible for management of artifacts used by rendering and layout preview surfaces.
 */
public interface RenderJarTracker {

  /** Builds the render jars of the given files and adds then to the cache */
  void buildRenderJarForFile(BlazeContext context, List<Path> workspaceRelativePaths)
      throws IOException, BuildException;
}
