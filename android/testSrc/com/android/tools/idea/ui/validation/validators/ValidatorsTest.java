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
package com.android.tools.idea.ui.validation.validators;

import com.android.tools.idea.ui.validation.Validator;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class ValidatorsTest {

  @Test
  public void trueValidatorWorks() throws Exception {
    TrueValidator validator = new TrueValidator("Dummy error message");
    assertThat(validator.validate(Boolean.TRUE).getSeverity()).isEqualTo(Validator.Severity.OK);
    assertThat(validator.validate(Boolean.FALSE).getSeverity()).isEqualTo(Validator.Severity.ERROR);

    TrueValidator infoValidator = new TrueValidator(Validator.Severity.INFO, "Dummy error message");
    assertThat(infoValidator.validate(Boolean.TRUE).getSeverity()).isEqualTo(Validator.Severity.OK);
    assertThat(infoValidator.validate(Boolean.FALSE).getSeverity()).isEqualTo(Validator.Severity.INFO);
  }

  @Test
  public void falseValidatorWorks() throws Exception {
    FalseValidator validator = new FalseValidator("Dummy error message");
    assertThat(validator.validate(Boolean.TRUE).getSeverity()).isEqualTo(Validator.Severity.ERROR);
    assertThat(validator.validate(Boolean.FALSE).getSeverity()).isEqualTo(Validator.Severity.OK);

    FalseValidator warnValidator = new FalseValidator(Validator.Severity.WARNING, "Dummy error message");
    assertThat(warnValidator.validate(Boolean.TRUE).getSeverity()).isEqualTo(Validator.Severity.WARNING);
    assertThat(warnValidator.validate(Boolean.FALSE).getSeverity()).isEqualTo(Validator.Severity.OK);
  }
}
