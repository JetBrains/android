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
package com.android.tools.idea.refactoring.modularize;

import com.android.SdkConstants;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;

public class AndroidModularizeHandlerTest extends AndroidTestCase {

  private static final String BASE_PATH = "refactoring/moveWithResources/";

  private AndroidModularizeProcessor myProcessor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject(BASE_PATH + "/res", "res/");
    myFixture.copyDirectoryToProject(BASE_PATH + "/src", "src/");
    myFixture.copyFileToProject(BASE_PATH + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);

    Module feature = TestModuleUtil.findModule(getProject(), "feature");
    ModuleRootModificationUtil.addDependency(feature, myModule);

    myProcessor = new AndroidModularizeHandler()
      .createProcessor(getProject(), new PsiElement[] { myFixture.getJavaFacade().findClass("google.MainActivity") });
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myProcessor = null;
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "feature", PROJECT_TYPE_LIBRARY, false);
    addModuleWithAndroidFacet(projectBuilder, modules, "base", PROJECT_TYPE_LIBRARY, true);
  }

  public void testPullUpDependency() {
    myProcessor.setTargetModule(TestModuleUtil.findModule(getProject(), "feature"));

    AndroidCodeAndResourcesGraph graph = myProcessor.getReferenceGraph();
    assertTrue("Util class is referenced from Other",
               graph.getReferencedOutsideScope().contains(myFixture.getJavaFacade().findClass("google.Util")));

    assertFalse(myProcessor.getShouldSelectAllReferences());
  }

  public void testPushDownDependency() {
    myProcessor.setTargetModule(TestModuleUtil.findModule(getProject(), "base"));

    AndroidCodeAndResourcesGraph graph = myProcessor.getReferenceGraph();
    assertTrue("Util class is referenced from Other",
               graph.getReferencedOutsideScope().contains(myFixture.getJavaFacade().findClass("google.Util")));

    assertTrue(myProcessor.getShouldSelectAllReferences());
  }
}
