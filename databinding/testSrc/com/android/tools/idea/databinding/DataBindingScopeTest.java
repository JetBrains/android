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
package com.android.tools.idea.databinding;

import static com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.EdtTestUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Rule;
import org.junit.Test;

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 */
public class DataBindingScopeTest {

  @Rule
  public final AndroidGradleProjectRule myProjectRule = new AndroidGradleProjectRule();

  @Test
  public void testAccessFromInaccessibleScope() {
    myProjectRule.getFixture().setTestDataPath(TestDataPaths.TEST_DATA_ROOT);
    myProjectRule.load(PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB);
    Project project = myProjectRule.getProject();
    AndroidFacet facet = myProjectRule.getAndroidFacet();
    // temporary fix until test model can detect dependencies properly
    GradleInvocationResult assembleDebug = myProjectRule.invokeTasks(project, "assembleDebug");
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    assertFalse(syncState.isSyncNeeded().toBoolean());
    assertSame(ModuleDataBinding.getInstance(facet).getDataBindingMode(), DataBindingMode.SUPPORT);
    assertTrue(myProjectRule.getModules().hasModule("lib"));
    assertTrue(myProjectRule.getModules().hasModule("lib2"));
    // app depends on lib depends on lib2

    EdtTestUtil.runInEdtAndWait(() -> {
      // trigger initialization
      ResourceRepositoryManager.getModuleResources(facet);

      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      String appBindingClassName = "com.android.example.appwithdatabinding.databinding.ActivityMainBinding";
      assertNotNull(javaPsiFacade.findClass(appBindingClassName, facet.getModule().getModuleWithDependenciesScope()));
      assertNull(
        javaPsiFacade.findClass(appBindingClassName, myProjectRule.getModules().getModule("lib").getModuleWithDependenciesScope()));
      assertNull(
        javaPsiFacade.findClass(appBindingClassName, myProjectRule.getModules().getModule("lib2").getModuleWithDependenciesScope()));

      // only exists in lib
      String libLayoutBindingClassName = "com.foo.bar.databinding.LibLayoutBinding";
      assertNotNull(javaPsiFacade.findClass(libLayoutBindingClassName, facet.getModule().getModuleWithDependenciesScope()));
      assertNotNull(
        javaPsiFacade.findClass(libLayoutBindingClassName, myProjectRule.getModules().getModule("lib").getModuleWithDependenciesScope()));
      assertNull(
        javaPsiFacade.findClass(libLayoutBindingClassName, myProjectRule.getModules().getModule("lib2").getModuleWithDependenciesScope()));

      // only exists in lib2
      String lib2LayoutBindingClassName = "com.foo.bar2.databinding.Lib2LayoutBinding";
      assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, facet.getModule().getModuleWithDependenciesScope()));
      assertNotNull(
        javaPsiFacade.findClass(lib2LayoutBindingClassName, myProjectRule.getModules().getModule("lib").getModuleWithDependenciesScope()));
      assertNotNull(
        javaPsiFacade.findClass(lib2LayoutBindingClassName, myProjectRule.getModules().getModule("lib2").getModuleWithDependenciesScope()));
    });
  }
}
