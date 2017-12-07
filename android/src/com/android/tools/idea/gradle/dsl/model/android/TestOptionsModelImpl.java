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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.TestOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.testOptions.UnitTestsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.testOptions.UnitTestsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement.UNIT_TESTS_BLOCK_NAME;

public class TestOptionsModelImpl extends GradleDslBlockModel implements TestOptionsModel {
  @NonNls private static final String REPORT_DIR = "reportDir";
  @NonNls private static final String RESULTS_DIR = "resultsDir";

  public TestOptionsModelImpl(@NotNull TestOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public GradleNullableValue<String> reportDir() {
    return myDslElement.getLiteralProperty(REPORT_DIR, String.class);
  }

  @Override
  @NotNull
  public TestOptionsModel setReportDir(@NotNull String reportDir) {
    myDslElement.setNewLiteral(REPORT_DIR, reportDir);
    return this;
  }

  @Override
  @NotNull
  public TestOptionsModel removeReportDir() {
    myDslElement.removeProperty(REPORT_DIR);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<String> resultsDir() {
    return myDslElement.getLiteralProperty(RESULTS_DIR, String.class);
  }

  @Override
  @NotNull
  public TestOptionsModel setResultsDir(@NotNull String resultsDir) {
    myDslElement.setNewLiteral(RESULTS_DIR, resultsDir);
    return this;
  }

  @Override
  @NotNull
  public TestOptionsModel removeResultsDir() {
    myDslElement.removeProperty(RESULTS_DIR);
    return this;
  }

  @Override
  @NotNull
  public UnitTestsModel unitTests() {
    UnitTestsDslElement unitTestsDslElement = myDslElement.getPropertyElement(UNIT_TESTS_BLOCK_NAME, UnitTestsDslElement.class);
    if (unitTestsDslElement == null) {
      unitTestsDslElement = new UnitTestsDslElement(myDslElement);
      myDslElement.setNewElement(UNIT_TESTS_BLOCK_NAME, unitTestsDslElement);
    }
    return new UnitTestsModelImpl(unitTestsDslElement);
  }
}
