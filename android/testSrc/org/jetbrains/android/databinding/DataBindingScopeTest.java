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
package org.jetbrains.android.databinding;

import com.android.tools.idea.databinding.ModuleDataBinding;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.psi.JavaPsiFacade;

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB;

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 */
public class DataBindingScopeTest extends AndroidGradleTestCase {

  public void testAccessFromInaccessibleScope() throws Exception {
    loadProject(PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB);
    // temporary fix until test model can detect dependencies properly
    GradleInvocationResult assembleDebug = invokeGradleTasks(getProject(), "assembleDebug");
    GradleSyncState syncState = GradleSyncState.getInstance(getProject());
    assertFalse(syncState.isSyncNeeded().toBoolean());
    assertTrue(ModuleDataBinding.isEnabled(myAndroidFacet));
    assertTrue(myModules.hasModule("lib"));
    assertTrue(myModules.hasModule("lib2"));
    // app depends on lib depends on lib2

    // trigger initialization
    ModuleResourceRepository.getOrCreateInstance(myAndroidFacet);

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    String appBindingClassName = "com.android.example.appwithdatabinding.databinding.ActivityMainBinding";
    assertNotNull(javaPsiFacade.findClass(appBindingClassName, myAndroidFacet.getModule().getModuleWithDependenciesScope()));
    assertNull(javaPsiFacade.findClass(appBindingClassName, myModules.getModule("lib").getModuleWithDependenciesScope()));
    assertNull(javaPsiFacade.findClass(appBindingClassName, myModules.getModule("lib2").getModuleWithDependenciesScope()));

    // only exists in lib
    String libLayoutBindingClassName = "com.foo.bar.databinding.LibLayoutBinding";
    assertNotNull(javaPsiFacade.findClass(libLayoutBindingClassName, myAndroidFacet.getModule().getModuleWithDependenciesScope()));
    assertNotNull(javaPsiFacade.findClass(libLayoutBindingClassName, myModules.getModule("lib").getModuleWithDependenciesScope()));
    assertNull(javaPsiFacade.findClass(libLayoutBindingClassName, myModules.getModule("lib2").getModuleWithDependenciesScope()));

    // only exists in lib2
    String lib2LayoutBindingClassName = "com.foo.bar2.databinding.Lib2LayoutBinding";
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, myAndroidFacet.getModule().getModuleWithDependenciesScope()));
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, myModules.getModule("lib").getModuleWithDependenciesScope()));
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, myModules.getModule("lib2").getModuleWithDependenciesScope()));
  }
}
