/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.TestActionEvent;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.MockitoAnnotations;

public class AndroidImportMlModelActionTest extends AndroidTestCase {

  private AndroidImportMlModelAction myAction;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    MockitoAnnotations.initMocks(this);
    StudioFlags.ML_MODEL_BINDING.override(true);
    myAction = new AndroidImportMlModelAction();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.ML_MODEL_BINDING.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testUpdateProjectIsNull() {
    TestActionEvent event = new TestActionEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT.getName(), null));

    myAction.update(event);

    assertFalse(event.getPresentation().isEnabled());
  }
}
