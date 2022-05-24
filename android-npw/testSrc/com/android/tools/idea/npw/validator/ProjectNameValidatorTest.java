/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.validation.Validator;
import org.junit.Before;
import org.junit.Test;

public final class ProjectNameValidatorTest {

  private ProjectNameValidator myProjectNameValidator;

  @Before
  public void createProjectValidator() {
    myProjectNameValidator = new ProjectNameValidator();
  }

  @Test
  public void testIsValidProjectName() {
    assertValidProjectName("My Application");
  }

  @Test
  public void testInvalidProjectName() {
    assertInvalidProjectName("");
    assertInvalidProjectName("My Application /");
    assertInvalidProjectName("My Application \\");
    assertInvalidProjectName("My Application :");
    assertInvalidProjectName("My Application <");
    assertInvalidProjectName("My Application >");
    assertInvalidProjectName("My Application \"");
    assertInvalidProjectName("My Application ?");
    assertInvalidProjectName("My Application *");
    assertInvalidProjectName("My Application |");
  }

  private void assertValidProjectName(String name) {
    Validator.Result result = myProjectNameValidator.validate(name);
    assertTrue(result.getMessage(), myProjectNameValidator.validate(name) == Validator.Result.OK);
  }

  private void assertInvalidProjectName(String name) {
    Validator.Result result = myProjectNameValidator.validate(name);
    assertFalse(result.getMessage(), myProjectNameValidator.validate(name) == Validator.Result.OK);
  }
}