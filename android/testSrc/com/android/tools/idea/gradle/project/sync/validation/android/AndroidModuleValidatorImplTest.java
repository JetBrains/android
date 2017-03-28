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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator.AndroidModuleValidatorImpl;
import com.intellij.openapi.module.Module;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidModuleValidatorImpl}.
 */
public class AndroidModuleValidatorImplTest {
  @Mock private AndroidProjectValidationStrategy myStrategy1;
  @Mock private AndroidProjectValidationStrategy myStrategy2;

  private AndroidModuleValidator myValidator;

  @Before
  public void setUp() {
    initMocks(this);
    myValidator = new AndroidModuleValidatorImpl(myStrategy1, myStrategy2);
  }

  @Test
  public void validate() {
    Module module = mock(Module.class);
    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);

    myValidator.validate(module, androidModel);

    verify(myStrategy1, times(1)).validate(module, androidModel);
    verify(myStrategy2, times(1)).validate(module, androidModel);
  }

  @Test
  public void fixAndReportFoundIssues() {
    myValidator.fixAndReportFoundIssues();

    verify(myStrategy1, times(1)).fixAndReportFoundIssues();
    verify(myStrategy2, times(1)).fixAndReportFoundIssues();
  }
}