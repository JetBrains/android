/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.test;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;

/**
 * A {@link Runner} that handles expected failures.
 * <p>
 * See {@link ExpectedFailuresRunNotifier}
 */
public class ExpectedFailureRunner extends Runner implements Filterable, Sortable {
  private final Runner delegate;

  public ExpectedFailureRunner(Runner delegate) {
    this.delegate = delegate;
  }

  @Override
  public Description getDescription() {
    return delegate.getDescription();
  }

  @Override
  public void run(RunNotifier runNotifier) {
    delegate.run(new ExpectedFailuresRunNotifier(runNotifier));
  }

  @Override
  public void filter(Filter filter) throws NoTestsRemainException {
    if (delegate instanceof Filterable) {
      ((Filterable)delegate).filter(filter);
    }
  }

  @Override
  public void sort(Sorter sorter) {
    if (delegate instanceof Sortable) {
      ((Sortable)delegate).sort(sorter);
    }
  }
}
