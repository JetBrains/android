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

import com.intellij.openapi.diagnostic.Logger;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.text.SimpleDateFormat;
import java.util.Date;

class LogStartAndStop extends TestWatcher {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
  private static final Logger LOG = Logger.getInstance(LogStartAndStop.class);

  private static void log(String s, Description description) {
    System.out.println(DATE_FORMAT.format(new Date()) + s + description.getDisplayName());
    LOG.info(s + description.getDisplayName());
  }

  @Override
  protected void starting(Description description) {
    log(" START ", description);
  }

  @Override
  protected void succeeded(Description description) {
    log(" PASS ", description);
  }

  @Override
  protected void failed(Throwable e, Description description) {
    log(" FAIL ", description);
  }

  @Override
  protected void skipped(AssumptionViolatedException e, Description description) {
    log(" SKIP ", description);
  }
}
