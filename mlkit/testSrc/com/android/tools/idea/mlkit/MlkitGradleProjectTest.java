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
package com.android.tools.idea.mlkit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MlkitGradleProjectTest {

  @Rule
  public final AndroidGradleProjectRule myProjectRule = new AndroidGradleProjectRule();

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @Before
  public void setUp() {
    StudioFlags.ML_MODEL_BINDING.override(true);
    myProjectRule.getFixture().setTestDataPath(TestDataPaths.TEST_DATA_ROOT);
    myProjectRule.load(TestDataPaths.PROJECT_WITH_TWO_MODULES_BUT_ONLY_ONE_ENABLED);
  }

  @After
  public void tearDown() {
    StudioFlags.ML_MODEL_BINDING.clearOverride();
  }

  @Test
  @RunsInEdt
  public void testModelClassGeneration() {
    Project project = myProjectRule.getProject();
    Module appModule = TestModuleUtil.findModule(project, "app");
    assertTrue(MlkitUtils.isMlModelBindingBuildFeatureEnabled(appModule));
    Module module2 = TestModuleUtil.findModule(project, "module2");
    assertFalse(MlkitUtils.isMlModelBindingBuildFeatureEnabled(module2));

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    assertNotNull(javaPsiFacade.findClass("google.withmlmodelbinding.ml.MyModel", GlobalSearchScope.moduleScope(appModule)));
    assertNull(javaPsiFacade.findClass("google.nomlmodelbinding.ml.MyModel2", GlobalSearchScope.moduleScope(module2)));
  }
}
