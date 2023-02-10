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
package com.android.tools.idea.databinding.gradle;

import static com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.TestDataPaths;
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Rule;
import org.junit.Test;

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 */
public class DataBindingScopeTest {

  @Rule
  public final AndroidGradleProjectRule myProjectRule = new AndroidGradleProjectRule();

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @Test
  @RunsInEdt
  public void testAccessFromInaccessibleScope() {
    myProjectRule.getFixture().setTestDataPath(TestDataPaths.TEST_DATA_ROOT);
    myProjectRule.load(PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB);
    Project project = myProjectRule.getProject();
    AndroidFacet facet = myProjectRule.androidFacet(":app");
    JavaCodeInsightTestFixture fixture = (JavaCodeInsightTestFixture)myProjectRule.getFixture();

    GradleSyncState syncState = GradleSyncState.getInstance(project);
    assertFalse(syncState.isSyncNeeded().toBoolean());
    assertSame(DataBindingMode.SUPPORT, LayoutBindingModuleCache.getInstance(facet).getDataBindingMode());

    // app depends on lib depends on lib2
    assertTrue(TestModuleUtil.hasModule(project, "app"));
    assertTrue(TestModuleUtil.hasModule(project, "lib"));
    assertTrue(TestModuleUtil.hasModule(project, "lib2"));

    GlobalSearchScope appScope = fixture.findClass("com.android.example.appwithdatabinding.MainActivity").getResolveScope();
    GlobalSearchScope libScope = fixture.findClass("lib.ContextPlaceholder").getResolveScope();
    GlobalSearchScope lib2Scope = fixture.findClass("lib2.ContextPlaceholder").getResolveScope();

    // trigger initialization
    StudioResourceRepositoryManager.getModuleResources(facet);
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);

    // app binding only accessible from app
    String appBindingClassName = "com.android.example.appwithdatabinding.databinding.ActivityMainBinding";
    assertNotNull(javaPsiFacade.findClass(appBindingClassName, appScope));
    assertNull(javaPsiFacade.findClass(appBindingClassName, libScope));
    assertNull(javaPsiFacade.findClass(appBindingClassName, lib2Scope));

    // lib binding accessible from app and lib
    String libLayoutBindingClassName = "com.foo.bar.databinding.LibLayoutBinding";
    assertNotNull(javaPsiFacade.findClass(libLayoutBindingClassName, appScope));
    assertNotNull(javaPsiFacade.findClass(libLayoutBindingClassName, libScope));
    assertNull(javaPsiFacade.findClass(libLayoutBindingClassName, lib2Scope));

    // lib2 binding accessible from app, lib, and lib2
    String lib2LayoutBindingClassName = "com.foo.bar2.databinding.Lib2LayoutBinding";
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, appScope));
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, libScope));
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, lib2Scope));
  }
}
