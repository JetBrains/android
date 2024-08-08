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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.intellij.openapi.roots.libraries.Library;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Interface for contributing libraries to sync. */
public interface LibrarySource {

  /** Called during the project structure phase to get libraries. */
  List<? extends BlazeLibrary> getLibraries();

  /**
   * Returns a filter on libraries.
   *
   * <p>Return false from the predicate to filter the library. If any filter returns false the
   * library is discarded.
   */
  @Nullable
  Predicate<BlazeLibrary> getLibraryFilter();

  /**
   * Returns a filter that allows sources to retain libraries during library garbage collection.
   *
   * <p>Return true from the predicate to spare a library during garbage collection. If any filter
   * returns true the library is spared.
   */
  @Nullable
  Predicate<Library> getGcRetentionFilter();

  /** Adapter class */
  abstract class Adapter implements LibrarySource {
    @Override
    public List<? extends BlazeLibrary> getLibraries() {
      return ImmutableList.of();
    }

    @Nullable
    @Override
    public Predicate<BlazeLibrary> getLibraryFilter() {
      return null;
    }

    @Nullable
    @Override
    public Predicate<Library> getGcRetentionFilter() {
      return null;
    }
  }
}
