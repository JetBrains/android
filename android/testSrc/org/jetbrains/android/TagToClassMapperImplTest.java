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
package org.jetbrains.android;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.SdkConstants;
import com.android.tools.idea.psi.TagToClassMapper;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;

public class TagToClassMapperImplTest extends AndroidTestCase {
  private static final String MODULE_WITH_DEPENDENCY = "withdep";
  private static final String MODULE_WITHOUT_DEPENDENCY = "withoutdep";
  private static final String OBJECT_CLASS = "java.lang.Object";

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, MODULE_WITH_DEPENDENCY, PROJECT_TYPE_LIBRARY, true);
    addModuleWithAndroidFacet(projectBuilder, modules, MODULE_WITHOUT_DEPENDENCY, PROJECT_TYPE_LIBRARY, false);
  }

  public void testModuleDeps() {
    @Language("java")
    String classA = "package com.test;" +
                    "" +
                    "public class ClassA {" +
                    "}";
    @Language("java")
    String classB = "package com.test.other;" +
                    "" +
                    "public class ClassB {" +
                    "}";

    myFixture.addFileToProject(getAdditionalModulePath(MODULE_WITH_DEPENDENCY) + "/src/com/test/ClassA.java", classA);
    myFixture.addFileToProject(getAdditionalModulePath(MODULE_WITHOUT_DEPENDENCY) + "/src/com/test/other/ClassB.java", classB);

    // The main module should only see the classes from MODULE_WITH_DEPENDENCY
    Set<String> classes = new TagToClassMapperImpl(myFacet.getModule()).getClassMap(OBJECT_CLASS).keySet();
    assertContainsElements(classes, "com.test.ClassA");
    assertDoesntContain(classes, "com.test.other.ClassB");

    // MODULE_WITH_DEPENDENCY should only see the classes from MODULE_WITH_DEPENDENCY
    classes = new TagToClassMapperImpl(getAdditionalModuleByName(MODULE_WITH_DEPENDENCY))
      .getClassMap(OBJECT_CLASS)
      .keySet();
    assertContainsElements(classes, "com.test.ClassA");
    assertDoesntContain(classes, "com.test.other.ClassB");

    // MODULE_WITHOUT_DEPENDENCY should see its own class
    classes = new TagToClassMapperImpl(getAdditionalModuleByName(MODULE_WITHOUT_DEPENDENCY))
      .getClassMap(OBJECT_CLASS)
      .keySet();
    assertDoesntContain(classes, "com.test.ClassA");
    assertContainsElements(classes, "com.test.other.ClassB");
  }

  private static class CountingMapper extends TagToClassMapperImpl {
    CountingMapper(@NotNull Module module) {
      super(module);
    }

    LongAdder fullRebuilds = new LongAdder();

    @NotNull
    @Override
    Map<String, SmartPsiElementPointer<PsiClass>> computeInitialClassMap(@NotNull String classMapKey) {
      fullRebuilds.increment();
      return super.computeInitialClassMap(classMapKey);
    }
  }

  public void testFullRebuilds() {
    CountingMapper countingMapper = new CountingMapper(myModule);

    // Use the counting mapper.
    ServiceContainerUtil.replaceService(myModule, TagToClassMapper.class, countingMapper, getTestRootDisposable());

    // Use a min API level that affects short names, to make sure it's used in up-to-date checks. See ResourceHelper.isViewPackageNeeded.
    runWriteCommandAction(getProject(), () -> Manifest.getMainManifest(myFacet).addUsesSdk().getMinSdkVersion().setValue("21"));

    countingMapper.getClassMap(SdkConstants.CLASS_VIEW);
    assertThat(countingMapper.fullRebuilds.longValue()).named("Number of full rebuilds").isEqualTo(1);

    PsiFile layoutFile = myFixture.addFileToProject("res/layout/my_layout.xml", "<LinearLayout><<caret></LinearLayout>");
    myFixture.configureFromExistingVirtualFile(layoutFile.getVirtualFile());
    myFixture.completeBasic();

    assertThat(countingMapper.fullRebuilds.longValue()).named("Number of full rebuilds").isEqualTo(1);
  }
}
