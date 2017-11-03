/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.google.common.io.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ModuleValidatorTest {
  private ModuleValidator myModuleValidator;
  private File myTmpDir;

  @Before
  public void createModuleValidator() {
    myTmpDir = Files.createTempDir();
    myModuleValidator = new ModuleValidator(new StringValueProperty(myTmpDir.getAbsolutePath()));

    // Hack for build bots. The directory used to run the tests in a sand box is very very long
    ((List<PathValidator.Rule>) myModuleValidator.getPathValidator().getErrors()).remove(PathValidator.PATH_TOO_LONG);
  }

  @After
  public void cleanModuleValidator() {
    boolean ignored = myTmpDir.delete();
  }

  @Test
  public void testIsValidModuleName() {
    assertValidModuleName("app");
    assertValidModuleName("lib");
  }

  @Test
  public void testInvalidModuleName() {
    assertInvalidModuleName("");
    assertInvalidModuleName("..");
    assertInvalidModuleName("123:456");
    assertInvalidModuleName("123!456");
  }

  @Test
  public void testIsInvalidWindowsModuleName() {
    String[] invalidWindowsFilenames = {"con", "prn", "aux", "clock$", "nul", "$boot"};
    for (String s : invalidWindowsFilenames) {
      assertInvalidModuleName(s);
    }
  }

  private void assertValidModuleName(String name) {
    Validator.Result result = myModuleValidator.validate(name);
    assertTrue(result.getMessage(), myModuleValidator.validate(name) == Validator.Result.OK);
  }

  private void assertInvalidModuleName(String name) {
    Validator.Result result = myModuleValidator.validate(name);
    assertFalse(result.getMessage(), myModuleValidator.validate(name) == Validator.Result.OK);
  }
}
