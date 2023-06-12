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

import static com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProjectKt.testProjectTemplateFromPath;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MlGradleProjectTest {

  @Rule
  public final AndroidProjectRule.Typed<JavaCodeInsightTestFixture, AndroidProjectRule.TestProjectTestHelpers> myProjectRule =
    AndroidProjectRule.testProject(
      testProjectTemplateFromPath(TestDataPaths.PROJECT_WITH_TWO_LIB_MODULES_BUT_ONLY_ONE_ENABLED, TestDataPaths.TEST_DATA_ROOT));

  @Before
  public void setUp() {
    StudioFlags.ML_MODEL_BINDING.override(true);
  }

  @After
  public void tearDown() {
    StudioFlags.ML_MODEL_BINDING.clearOverride();
  }

  @Test
  @RunsInEdt
  public void testBuildFeatureFlag() {
    Project project = myProjectRule.getProject();
    Module appModule = TestModuleUtil.findModule(project, "app");
    assertTrue(MlUtils.isMlModelBindingBuildFeatureEnabled(appModule));
    Module libOnModule = TestModuleUtil.findModule(project, "lib_on");
    assertTrue(MlUtils.isMlModelBindingBuildFeatureEnabled(libOnModule));
    Module libOffModule = TestModuleUtil.findModule(project, "lib_off");
    assertFalse(MlUtils.isMlModelBindingBuildFeatureEnabled(libOffModule));
  }

  @Test
  @RunsInEdt
  public void testLightClassSearchScope() {
    Project project = myProjectRule.getProject();
    // App depends on lib_on and lib_off.
    assertTrue(TestModuleUtil.hasModule(project, "app"));
    assertTrue(TestModuleUtil.hasModule(project, "lib_on"));
    assertTrue(TestModuleUtil.hasModule(project, "lib_off"));

    JavaCodeInsightTestFixture fixture = myProjectRule.getFixture();
    GlobalSearchScope appScope = fixture.findClass("com.mlmodelbinding.MyActivity").getResolveScope();
    GlobalSearchScope libOnScope = fixture.findClass("lib.withbinding.Sample").getResolveScope();
    GlobalSearchScope libOffScope = fixture.findClass("lib.nobinding.Sample").getResolveScope();

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);

    // Verify app can access light class from itself and the binding enabled lib.
    assertNotNull(javaPsiFacade.findClass("com.mlmodelbinding.ml.AppModel", appScope));
    assertNotNull(javaPsiFacade.findClass("lib.withbinding.ml.LibModel", appScope));
    assertNull(javaPsiFacade.findClass("lib.nobinding.ml.LibModel", appScope));

    // Verify the binding enabled lib can only access light class from itself.
    assertNotNull(javaPsiFacade.findClass("lib.withbinding.ml.LibModel", libOnScope));
    assertNull(javaPsiFacade.findClass("com.mlmodelbinding.ml.AppModel", libOnScope));
    assertNull(javaPsiFacade.findClass("lib.nobinding.ml.LibModel", libOnScope));

    // Verify the binding disabled lib has no light class and can not access light class from anywhere.
    assertNull(javaPsiFacade.findClass("lib.nobinding.ml.LibModel", libOffScope));
    assertNull(javaPsiFacade.findClass("lib.withbinding.ml.LibModel", libOffScope));
    assertNull(javaPsiFacade.findClass("com.mlmodelbinding.ml.AppModel", libOffScope));
  }
}
