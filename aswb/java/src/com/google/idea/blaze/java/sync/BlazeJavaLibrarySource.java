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
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

class BlazeJavaLibrarySource extends LibrarySource.Adapter {

  private final BlazeProjectData blazeProjectData;

  BlazeJavaLibrarySource(BlazeProjectData blazeProjectData) {
    this.blazeProjectData = blazeProjectData;
  }

  @Override
  public List<? extends BlazeLibrary> getLibraries() {
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return ImmutableList.of();
    }
    return syncData.getImportResult().libraries.values().asList();
  }

  @Nullable
  @Override
  public Predicate<BlazeLibrary> getLibraryFilter() {
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return o -> true;
    }
    return new LibraryGlobFilter(syncData.getExcludedLibraries());
  }
}
