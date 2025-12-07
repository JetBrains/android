/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.java.projectview.ExcludeLibrarySection;
import com.google.idea.blaze.java.projectview.ExcludedLibrarySection;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import java.util.Collection;
import javax.annotation.Nullable;

/** Sync support for Java. */
public class BlazeJavaSyncPlugin implements BlazeSyncPlugin {
  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.JAVA);
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.JAVA;
  }
  @Override
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(
        ExcludedLibrarySection.PARSER,
        ExcludeLibrarySection.PARSER,
        JavaLanguageLevelSection.PARSER);
  }
}
