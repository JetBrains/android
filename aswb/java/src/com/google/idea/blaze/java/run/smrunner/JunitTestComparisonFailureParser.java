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
package com.google.idea.blaze.java.run.smrunner;

import com.google.idea.blaze.base.run.smrunner.TestComparisonFailureParser;
import com.intellij.junit4.ExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import javax.annotation.Nullable;

/** {@link TestComparisonFailureParser} for JUnit-like tests. */
public class JunitTestComparisonFailureParser implements TestComparisonFailureParser {
  @Nullable
  @Override
  public BlazeComparisonFailureData tryParse(String message) {
    ComparisonFailureData comparisonFailureData =
        ExpectedPatterns.createExceptionNotification(message);
    if (comparisonFailureData == null) {
      return null;
    }
    return new BlazeComparisonFailureData(
        comparisonFailureData.getActual(), comparisonFailureData.getExpected());
  }
}
