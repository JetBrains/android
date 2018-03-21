// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework;

import org.jetbrains.annotations.NotNull;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

public class SingleClassFilter extends Filter {

  private final String className;

  /**
   * {@code className} is the fully qualified class name of a test class.
   * Only test methods that are a part of the test class named by
   * {@code className} will be allowed to run.
   */
  public SingleClassFilter(@NotNull String className) {
    this.className = className;
  }

  @Override
  public boolean shouldRun(Description description) {
    // Don't filter out suites. They may contain our class!
    return description.isSuite() || className.equals(description.getClassName());
  }

  @Override
  public String describe() {
    return SingleClassFilter.class.getSimpleName() + " for " + className;
  }
}
