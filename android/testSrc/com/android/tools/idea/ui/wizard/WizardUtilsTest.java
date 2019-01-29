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
package com.android.tools.idea.ui.wizard;

import com.android.tools.adtui.validation.Validator.Result;
import com.android.tools.adtui.validation.Validator.Severity;
import org.junit.Test;

import static com.android.tools.adtui.validation.Validator.Result.OK;
import static com.android.tools.idea.ui.wizard.WizardUtils.getUniqueName;
import static com.android.tools.idea.ui.wizard.WizardUtils.validatePackageName;
import static com.google.common.truth.Truth.assertThat;
import static org.gradle.internal.impldep.org.apache.commons.lang.StringUtils.repeat;

public class WizardUtilsTest {
  private final Result ERROR = new Result(Severity.ERROR, "Some error message");
  private final Result WARNING = new Result(Severity.WARNING, "Some warning message");
  private final Result INFO = new Result(Severity.INFO, "Some info message");

  @Test
  public void getUniqueNameForAlwaysOKResult() {
    assertThat(getUniqueName("test", value -> OK)).isEqualTo("test");
  }

  @Test
  public void getUniqueNameForAlwaysERRORResult() {
    assertThat(getUniqueName("test", value -> ERROR)).isEqualTo("test100");
  }

  @Test
  public void getUniqueNameForAlwaysWarningResult() {
    assertThat(getUniqueName("test", value -> WARNING)).isEqualTo("test");
  }

  @Test
  public void getUniqueNameForAlwaysInfoResult() {
    assertThat(getUniqueName("test", value -> INFO)).isEqualTo("test");
  }

  @Test
  public void getUniqueNameForSomeOKResult() {
    assertThat(getUniqueName("test", value -> value.endsWith("9") ? OK : ERROR)).isEqualTo("test9");
  }

  @Test
  public void validatePackageNameWithNullPackage() {
    assertThat(validatePackageName(null)).isEqualTo("Package name is missing");
  }

  @Test
  public void validatePackageNameWithLongPackage() {
    assertThat(validatePackageName(repeat("A", 200))).isEqualTo("Package name is too long");
  }
}