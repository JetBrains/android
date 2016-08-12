/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;

/**
 * A facade for running JUnit tests without an Ant task, suitable for running UI tests on the Studio binary.
 */
public class PlainJUnitRunnable implements Runnable {

  private final Class<?> mSuiteClass;

  public PlainJUnitRunnable(Class<?> suiteClass) {
    mSuiteClass = suiteClass;
  }

  @Override
  public void run() {
    JUnitCore junit = new JUnitCore();
    junit.addListener(new TextListener(System.out));
    if (!junit.run(Request.aClass(mSuiteClass)).wasSuccessful()) {
      System.exit(1);
    }
  }
}
