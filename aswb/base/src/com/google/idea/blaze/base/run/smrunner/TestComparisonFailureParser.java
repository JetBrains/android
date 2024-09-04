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
package com.google.idea.blaze.base.run.smrunner;

import com.intellij.openapi.extensions.ExtensionPointName;
import javax.annotation.Nullable;

/** Parses test case failure messages to give actual/expected text comparisons. */
public interface TestComparisonFailureParser {
  ExtensionPointName<TestComparisonFailureParser> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.TestComparisonFailureParser");

  static BlazeComparisonFailureData parse(String message) {
    for (TestComparisonFailureParser parser : EP_NAME.getExtensions()) {
      BlazeComparisonFailureData data = parser.tryParse(message);
      if (data != null) {
        return data;
      }
    }
    return BlazeComparisonFailureData.NONE;
  }

  @Nullable
  BlazeComparisonFailureData tryParse(String message);

  /** Data class for actual/expected text. */
  class BlazeComparisonFailureData {
    static final BlazeComparisonFailureData NONE = new BlazeComparisonFailureData(null, null);
    @Nullable public final String actual;
    @Nullable public final String expected;

    public BlazeComparisonFailureData(@Nullable String actual, @Nullable String expected) {
      this.actual = actual;
      this.expected = expected;
    }
  }
}
