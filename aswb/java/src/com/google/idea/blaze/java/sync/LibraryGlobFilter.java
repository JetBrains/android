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

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.projectview.section.Glob.GlobSet;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import java.util.function.Predicate;

/** Filters out any libraries in a globset. */
public class LibraryGlobFilter implements Predicate<BlazeLibrary> {

  private final GlobSet excludedLibraries;

  public LibraryGlobFilter(GlobSet excludedLibraries) {
    this.excludedLibraries = excludedLibraries;
  }

  @Override
  public boolean test(BlazeLibrary blazeLibrary) {
    if (!(blazeLibrary instanceof BlazeJarLibrary)) {
      return true;
    }
    BlazeJarLibrary jarLibrary = (BlazeJarLibrary) blazeLibrary;
    ArtifactLocation interfaceJar = jarLibrary.libraryArtifact.getInterfaceJar();
    ArtifactLocation classJar = jarLibrary.libraryArtifact.getClassJar();
    boolean matches =
        (interfaceJar != null && excludedLibraries.matches(interfaceJar.getRelativePath()))
            || (classJar != null && excludedLibraries.matches(classJar.getRelativePath()));
    return !matches;
  }
}
