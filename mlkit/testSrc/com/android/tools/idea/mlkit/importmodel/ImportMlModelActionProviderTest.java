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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wizard.template.Category;
import com.intellij.openapi.actionSystem.AnAction;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Test;

/**
 * Unit tests for {@link ImportMlModelActionsProvider}.
 */
public class ImportMlModelActionProviderTest extends AndroidTestCase {
  private ImportMlModelActionsProvider myImportMlModelActionsProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.ML_MODEL_BINDING.override(true);
    myImportMlModelActionsProvider = new ImportMlModelActionsProvider();
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

  public void testGetAdditionalActions_categoryOtherWithFlag_returnAction() {
    StudioFlags.ML_MODEL_BINDING.override(true);
    List<AnAction> actions = myImportMlModelActionsProvider.getAdditionalActions(Category.Other);
    assertThat(actions).hasSize(1);
    assertThat(actions.get(0)).isInstanceOf(ImportMlModelAction.class);
  }

  public void testGetAdditionalActions_flagOff_returnEmpty() {
    StudioFlags.ML_MODEL_BINDING.override(false);
    assertThat(myImportMlModelActionsProvider.getAdditionalActions(Category.Other)).isEmpty();
  }

  public void testGetAdditionalActions_categoryNotOther_returnEmpty() {
    StudioFlags.ML_MODEL_BINDING.override(true);
    assertThat(myImportMlModelActionsProvider.getAdditionalActions(Category.Activity)).isEmpty();
  }
}
