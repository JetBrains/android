/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.model.BlazeLibrary;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.List;

/**
 * Sorts project libraries found during sync. Sorters are invoked in reverse EP order, so the
 * highest-priority sorter should appear first in the EP list.
 */
public interface BlazeLibrarySorter {

  ExtensionPointName<BlazeLibrarySorter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeLibrarySorter");

  static List<BlazeLibrary> sortLibraries(List<BlazeLibrary> libraries) {
    BlazeLibrarySorter[] sorters = EP_NAME.getExtensions();
    for (int i = sorters.length - 1; i >= 0; i--) {
      libraries = sorters[i].sort(libraries);
    }
    return libraries;
  }

  List<BlazeLibrary> sort(List<BlazeLibrary> libraries);
}
