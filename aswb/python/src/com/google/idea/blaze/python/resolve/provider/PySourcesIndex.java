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
package com.google.idea.blaze.python.resolve.provider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.intellij.psi.util.QualifiedName;

/** An index of python sources and their associated import strings. */
class PySourcesIndex {
  final ImmutableSetMultimap<String, QualifiedName> shortNames;
  final ImmutableMap<QualifiedName, PsiElementProvider> sourceMap;

  PySourcesIndex(
      ImmutableSetMultimap<String, QualifiedName> shortNames,
      ImmutableMap<QualifiedName, PsiElementProvider> sourceMap) {
    this.shortNames = shortNames;
    this.sourceMap = sourceMap;
  }
}
