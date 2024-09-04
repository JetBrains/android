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
package com.google.idea.blaze.base.targetmaps;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.function.Supplier;

/** Maps source files to their respective targets */
public class DelegatingSourceToTargetMap implements SourceToTargetMap {
  private final Supplier<SourceToTargetMap> delegateSupplier;

  public DelegatingSourceToTargetMap(Project project) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      delegateSupplier = QuerySyncManager.getInstance(project)::getSourceToTargetMap;
    } else {
      delegateSupplier = Suppliers.ofInstance(new AspectSyncSourceToTargetMap(project));
    }
  }

  @Override
  public ImmutableList<Label> getTargetsToBuildForSourceFile(File sourceFile) {
    return delegateSupplier.get().getTargetsToBuildForSourceFile(sourceFile);
  }

  @Override
  public ImmutableCollection<TargetKey> getRulesForSourceFile(File sourceFile) {
    return delegateSupplier.get().getRulesForSourceFile(sourceFile);
  }
}
