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
package com.google.idea.blaze.base.sync.libraries;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Collects libraries from the sync data using all contributors. */
public class BlazeLibraryCollector {
  public static List<BlazeLibrary> getLibraries(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    return collectLibraries(projectViewSet, blazeProjectData, LibrarySource::getLibraries);
  }

  /* Collect all libraries of specific types from all library sources. */
  private static List<BlazeLibrary> collectLibraries(
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Function<LibrarySource, List<? extends BlazeLibrary>> librarySelector) {
    // Use set to filter out duplicates.
    Set<BlazeLibrary> result = Sets.newLinkedHashSet();
    List<LibrarySource> librarySources = Lists.newArrayList();
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      LibrarySource librarySource = syncPlugin.getLibrarySource(projectViewSet, blazeProjectData);
      if (librarySource != null) {
        librarySources.add(librarySource);
      }
    }
    for (LibrarySource librarySource : librarySources) {
      result.addAll(librarySelector.apply(librarySource));
    }
    Predicate<BlazeLibrary> libraryFilter =
        librarySources.stream()
            .map(LibrarySource::getLibraryFilter)
            .filter(Objects::nonNull)
            .reduce(Predicate::and)
            .orElse(o -> true);

    return BlazeLibrarySorter.sortLibraries(
        result.stream().filter(libraryFilter).collect(Collectors.toList()));
  }
}
