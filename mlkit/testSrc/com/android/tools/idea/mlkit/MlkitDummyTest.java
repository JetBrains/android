/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.testFramework.VfsTestUtil;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;

public class MlkitDummyTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.override(true);
    StudioFlags.MLKIT_LIGHT_CLASSES.override(true);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.clearOverride();
      StudioFlags.MLKIT_LIGHT_CLASSES.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testModuleService() throws Exception {
    VfsTestUtil.createFile(ProjectUtil.guessProjectDir(getProject()), "assets/my_model.tflite", new byte[]{1, 2, 3});

    MlkitModuleService mlkitService = MlkitModuleService.getInstance(myModule);
    List<LightModelClass> lightClasses = mlkitService.getLightModelClassList();
    assertThat(lightClasses).hasSize(1);
    LightModelClass lightClass = Iterables.getOnlyElement(lightClasses);
    assertThat(lightClass.getName()).isEqualTo("MyModel");
    assertThat(ModuleUtilCore.findModuleForPsiElement(lightClass)).isEqualTo(myModule);
  }
}
