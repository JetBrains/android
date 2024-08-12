/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.sync;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonFacetSettings;
import javax.annotation.Nullable;

/**
 * A helper class to work-around upstream python facet hacks (e.g. there are different python facet
 * classes in different IDEs).
 */
class PythonFacetUtil {

  private static final String ID = "Python";

  static boolean usePythonFacets() {
    return !PlatformUtils.isPyCharm();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static FacetType<? extends LibraryContributingFacet<?>, ?> getFacetType() {
    for (FacetType type : FacetType.EP_NAME.getExtensions()) {
      if (ID.equals(type.getStringId())) {
        return type;
      }
    }
    throw new RuntimeException("No Python facet type found");
  }

  static FacetTypeId<? extends LibraryContributingFacet<?>> getFacetId() {
    return getFacetType().getId();
  }

  @Nullable
  static Sdk getSdk(LibraryContributingFacet<?> facet) {
    FacetConfiguration config = facet.getConfiguration();
    return config instanceof PythonFacetSettings ? ((PythonFacetSettings) config).getSdk() : null;
  }
}
