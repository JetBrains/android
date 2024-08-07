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
package com.google.idea.blaze.base.vcs;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * This class exists to encapsulate private functionality related to {@link BlazeVcsHandlerProvider}
 * which cannot be made private in there since it is an interface.
 */
class BlazeVcsHandlerCache {

  private static final NotNullLazyKey<Optional<BlazeVcsHandler>, Project> VCS_HANDLER_KEY =
      NotNullLazyKey.create("BlazeVcsHandler", BlazeVcsHandlerCache::createVcsHandlerForProject);

  private BlazeVcsHandlerCache() {}

  @Nullable
  static BlazeVcsHandlerProvider vcsHandlerProviderForProject(Project project) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    for (BlazeVcsHandlerProvider candidate : BlazeVcsHandlerProvider.EP_NAME.getExtensions()) {
      if (candidate.handlesProject(project, workspaceRoot)) {
        return candidate;
      }
    }
    return null;
  }

  @Nullable
  static BlazeVcsHandler vcsHandlerForProject(Project project) {
    return VCS_HANDLER_KEY.getValue(project).orElse(null);
  }

  private static Optional<BlazeVcsHandler> createVcsHandlerForProject(Project project) {
    BlazeVcsHandlerProvider provider = vcsHandlerProviderForProject(project);
    return provider == null
        ? Optional.empty()
        : Optional.ofNullable(provider.getHandlerForProject(project));
  }
}
