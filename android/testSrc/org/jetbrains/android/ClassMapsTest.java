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
package org.jetbrains.android;

import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;

public class ClassMapsTest extends AndroidTestCase {
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
    Set<String> classes = new ClassMaps(myFacet.getModule()).getClassMap(OBJECT_CLASS).keySet();
    assertContainsElements(classes, "com.test.ClassA");
    assertDoesntContain(classes, "com.test.other.ClassB");

    // MODULE_WITH_DEPENDENCY should only see the classes from MODULE_WITH_DEPENDENCY
    classes = new ClassMaps(getAdditionalModuleByName(MODULE_WITH_DEPENDENCY))
      .getClassMap(OBJECT_CLASS)
      .keySet();
    assertContainsElements(classes, "com.test.ClassA");
    assertDoesntContain(classes, "com.test.other.ClassB");

    // MODULE_WITHOUT_DEPENDENCY should see its own class
    classes = new ClassMaps(getAdditionalModuleByName(MODULE_WITHOUT_DEPENDENCY))
      .getClassMap(OBJECT_CLASS)
      .keySet();
    assertDoesntContain(classes, "com.test.ClassA");
    assertContainsElements(classes, "com.test.other.ClassB");

  }
}