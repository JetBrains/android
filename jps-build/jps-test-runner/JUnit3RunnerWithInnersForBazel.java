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

import java.util.ArrayList;
import java.util.Collections;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;

public class JUnit3RunnerWithInnersForBazel extends Runner implements Filterable, Sortable {
  private final JUnit38ClassRunner delegate;

  public JUnit3RunnerWithInnersForBazel(Class<?> aClass) {
    TestSuite suite = new TestSuite(aClass.getCanonicalName());

    addClassTests(aClass, suite);
    this.delegate = new JUnit38ClassRunner(suite);
  }


  private static void addClassTests(Class<?> aClass, TestSuite suite) {
    ArrayList<Test> tests = Collections.list(new TestSuite(aClass).tests());
    if (tests.size() == 1 && tests.get(0) instanceof TestCase && ((TestCase)tests.get(0)).getName().equals("warning")) {
      // ignore
    } else {
      for (Test test : tests) {
        suite.addTest(test);
      }
    }
    for (Class<?> inner : aClass.getDeclaredClasses()) {
      if (!hasRunWith(inner)) {
        addClassTests(inner, suite);
      }
    }
  }

  private static boolean hasRunWith(Class<?> inner) {
    return inner.getAnnotation(RunWith.class) != null;
  }

  @Override
  public Description getDescription() {
    return delegate.getDescription();
  }

  @Override
  public void run(RunNotifier runNotifier) {
    delegate.run(runNotifier);
  }

  @Override
  public void filter(Filter filter) throws NoTestsRemainException {
    delegate.filter(filter);
  }

  @Override
  public void sort(Sorter sorter) {
    delegate.sort(sorter);
  }
}
