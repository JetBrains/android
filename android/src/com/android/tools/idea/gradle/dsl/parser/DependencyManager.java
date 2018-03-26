/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to manage unresolved dependencies.
 */
public final class DependencyManager {
  @NotNull private final List<GradleReferenceInjection> myUnresolvedReferences = new ArrayList<>();

  public static DependencyManager create() {
    return new DependencyManager();
  }

  private DependencyManager() {
  }

  /**
   * Registers a new unresolved dependency.
   */
  public void registerUnresolvedReference(@NotNull GradleReferenceInjection injection) {
    // Make sure the reference is not resolved.
    assert !injection.isResolved();
    myUnresolvedReferences.add(injection);
  }

  /**
   * Unregisters an unresolved dependency.
   */
  public void unregisterUnresolvedReference(@NotNull GradleReferenceInjection injection) {
    // Make sure the reference is not resolved.
    assert !injection.isResolved();
    myUnresolvedReferences.remove(injection);
  }

  /**
   * Attempt to resolve dependencies related to a change in a given element.
   * This currently just delegates to {@link #resolveAll()}
   *
   * @param element the element that has triggered the attempted resolve. This is currently unused.
   */
  public void resolveWith(@NotNull GradleDslElement element) {
    resolveAll();
  }

  /**
   * Attempt to resolve all of the current unresolved dependencies.
   */
  public void resolveAll() {
    for (Iterator<GradleReferenceInjection> it = myUnresolvedReferences.iterator(); it.hasNext();) {
      GradleReferenceInjection injection = it.next();
      // Attempt to re-resolve any references.
      GradleDslElement newElement = injection.getOriginElement().resolveReference(injection.getName(), true);
      if (newElement != null) {
        assert newElement instanceof GradleDslExpression ||
               newElement instanceof GradleDslExpressionList ||
               newElement instanceof GradleDslExpressionMap;
        injection.resolveWith(newElement);
        newElement.registerDependent(injection);
        it.remove();
      }
    }
  }
}
