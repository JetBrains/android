/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.dependencies.CommonConfigurationNames;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public class ModuleDependencyTest extends GradleFileModelTestCase {
  public void testCompactNotation() throws Exception {
    String text = "dependencies {\n" +
                  "  compile project(':a')\n" +
                  "}";

    checkReadAndModify(text);
  }

  public void testMapNotation() throws Exception {
    String text = "dependencies {\n" +
                  "  compile project(path: ':a')\n" +
                  "}";

    checkReadAndModify(text);
  }

  private void checkReadAndModify(@NotNull String text) throws IOException {
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();

    // Read
    ModuleDependencyModel dependencyModel = getDependency(buildModel.dependenciesV2());
    assertEquals(":a", dependencyModel.path());
    assertNull(dependencyModel.configuration());

    // Set Config
    dependencyModel.setConfiguration("myConf");
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    buildModel.reparse();

    dependencyModel = getDependency(buildModel.dependenciesV2());
    assertEquals(":a", dependencyModel.path());
    assertEquals("myConf", dependencyModel.configuration());

    // Set Project
    dependencyModel.setPath(":new_a");
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    buildModel.reparse();

    dependencyModel = getDependency(buildModel.dependenciesV2());
    assertEquals(":new_a", dependencyModel.path());
    assertEquals("myConf", dependencyModel.configuration());

    // Remove Config
    dependencyModel.removeConfiguration();
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    buildModel.reparse();

    dependencyModel = getDependency(buildModel.dependenciesV2());
    assertEquals(":new_a", dependencyModel.path());
    assertNull(dependencyModel.configuration());


    // Delete the dependency
    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);
    dependenciesModel.remove(dependencyModel);
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();
    assertNull(buildModel.dependenciesV2());
  }

  public void testMapNotationWithConfiguration() throws Exception {
    String text = "dependencies {\n" +
                  "  compile project(path: ':a', configuration: 'myConf')\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();

    ModuleDependencyModel dependency = getDependency(buildModel.dependenciesV2());

    assertEquals(":a", dependency.path());
    assertEquals("myConf", dependency.configuration());
  }

  public void testAddingDependency() throws Exception {
    String text = "dependencies {\n" +
                  "  compile project(':a')\n" +
                  "}";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);

    ModuleDependencyModel moduleDependency = getDependency(dependenciesModel);
    assertEquals(":a", moduleDependency.path());
    assertNull(moduleDependency.configuration());

    dependenciesModel.addModuleDependency("compile", ":b", "myConfig");

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);

    List<ModuleDependencyModel> moduleDependencies = dependenciesModel.moduleDependencies("compile");
    assertSize(2, moduleDependencies);

    assertEquals(":a", moduleDependencies.get(0).path());
    assertNull(moduleDependencies.get(0).configuration());
    assertEquals(":b", moduleDependencies.get(1).path());
    assertEquals("myConfig", moduleDependencies.get(1).configuration());
  }

  public void testAddingDependencyForEmptyFile() throws Exception {
    writeToBuildFile("");
    final GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNull(dependenciesModel);
    dependenciesModel = buildModel.addDependenciesModelV2().dependenciesV2();
    assertNotNull(dependenciesModel);

    dependenciesModel.addModuleDependency("compile", ":a", null);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);

    ModuleDependencyModel moduleDependency = getDependency(dependenciesModel);
    assertEquals(":a", moduleDependency.path());
    assertNull(moduleDependency.configuration());
  }

  private static ModuleDependencyModel getDependency(@Nullable DependenciesModel dependenciesModel) {
    assertNotNull(dependenciesModel);
    List<ModuleDependencyModel> moduleDependencies = dependenciesModel.moduleDependencies(CommonConfigurationNames.COMPILE);
    assertSize(1, moduleDependencies);
    return moduleDependencies.get(0);
  }
}
