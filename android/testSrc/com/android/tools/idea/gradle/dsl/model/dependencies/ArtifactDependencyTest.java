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

public class ArtifactDependencyTest extends GradleFileModelTestCase {
  public void testMapNotation() throws Exception {
    String text = "dependencies {\n" +
                  "  compile group: 'com.google.code.guice', name: 'guice', version: '1.0'\n" +
                  "}";

    checkReadAndModify(text);
  }

  public void testCompactNotation() throws Exception {
    String text = "dependencies {\n" +
                  "  compile 'com.google.code.guice:guice:1.0'\n" +
                  "}";
    checkReadAndModify(text);
  }

  public void testMapNotationMethodCall() throws Exception {
    String text = "dependencies {\n" +
                  "  compile(group: 'com.google.code.guice', name: 'guice', version: '1.0')\n" +
                  "}";
    checkReadAndModify(text);
  }

  public void testCompactNotationMethodCall() throws Exception {
    String text = "dependencies {\n" +
                  "  compile('com.google.code.guice:guice:1.0')\n" +
                  "}";
    checkReadAndModify(text);
  }

  public void testDependenciesWithSingleConfigurationName() throws Exception {
    String text = "dependencies {\n" +
                  "  compile(['com.google.code.guice:guice:1.0',\n" +
                  "           'com.google.guava:guava:19.0'])\n" +
                  "}";
    checkTwoDependenciesWithSingleConfigurationName(text);

    text = "dependencies {\n" +
           "  compile('com.google.code.guice:guice:1.0',\n" +
           "          'com.google.guava:guava:19.0')\n" +
           "}";
    checkTwoDependenciesWithSingleConfigurationName(text);

    text = "dependencies {\n" +
           "  compile(group: 'com.google.code.guice', name: 'guice', version: '1.0',\n" +
           "          group: 'com.google.guava', name: 'guava', version: '19.0')\n" +
           "}";
    checkTwoDependenciesWithSingleConfigurationName(text);

    text = "dependencies {\n" +
           "  compile([group: 'com.google.code.guice', name: 'guice', version: '1.0',\n" +
           "           group: 'com.google.guava', name: 'guava', version: '19.0'])\n" +
           "}";
    checkTwoDependenciesWithSingleConfigurationName(text);

    text = "dependencies {\n" +
           "  compile([group: 'com.google.code.guice', name: 'guice', version: '1.0',\n" +
           "           'com.google.guava:guava:19.0'])\n" +
           "}";
    checkTwoDependenciesWithSingleConfigurationName(text);
  }

  private void checkTwoDependenciesWithSingleConfigurationName(@NotNull String text) throws Exception {
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);
    List<ArtifactDependencyModel> artifacts = dependenciesModel.artifactDependencies("compile");
    assertSize(2, artifacts);


    // TODO fix GradleDslMethodCall in order to support deletion
    //dependenciesModel.remove(artifacts.get(0));
    //runWriteCommandAction(myProject, new Runnable() {
    //  @Override
    //  public void run() {
    //    buildModel.applyChanges();
    //  }
    //});
    //
    //buildModel.reparse();
    //dependenciesModel = buildModel.dependenciesV2();
    //assertNotNull(dependenciesModel);
    //
    //assertSize(1, dependenciesModel.artifactDependencies("compile"));
    //
    //dependenciesModel.remove(getDependency(dependenciesModel));
    //runWriteCommandAction(myProject, new Runnable() {
    //  @Override
    //  public void run() {
    //    buildModel.applyChanges();
    //  }
    //});
    //
    //buildModel.reparse();
    //dependenciesModel = buildModel.dependenciesV2();
    //assertNull(dependenciesModel);

  }

  public void testAddingDependency() throws Exception {
    String text = "dependencies {\n" +
                  "  compile 'com.google.code.guice:guice:1.0'\n" +
                  "}";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);

    assertEquals("com.google.code.guice:guice:1.0", getDependency(dependenciesModel).getCompactNotation());

    dependenciesModel.addArtifactDependency("compile", "com.google.guava:guava:19.0");

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);

    List<ArtifactDependencyModel> artifacts = dependenciesModel.artifactDependencies("compile");
    assertSize(2, artifacts);

    assertEquals("com.google.code.guice:guice:1.0", artifacts.get(0).getCompactNotation());
    assertEquals("com.google.guava:guava:19.0", artifacts.get(1).getCompactNotation());
  }

  public void testAddingDependencyForEmptyFile() throws Exception {
    writeToBuildFile("");
    final GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNull(dependenciesModel);
    dependenciesModel = buildModel.addDependenciesModelV2().dependenciesV2();
    assertNotNull(dependenciesModel);

    dependenciesModel.addArtifactDependency("compile", "com.google.code.guice:guice:1.0");

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);

    assertEquals("com.google.code.guice:guice:1.0", getDependency(dependenciesModel).getCompactNotation());
  }

  public void testAddingInvalidDependency() throws Exception {
    String text = "dependencies {\n" +
                  "}";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);

    try {
      dependenciesModel.addArtifactDependency("compile", "1:2:3:4:5:6");
      fail();
    } catch (IllegalArgumentException e) {
      // ingore
    }

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);
    assertEmpty(dependenciesModel.artifactDependencies("compile"));
  }

  private void checkReadAndModify(@NotNull String text) throws IOException {
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    ArtifactDependencyModel dependencyModel = getDependency(buildModel.dependenciesV2());

    // Test Read
    assertEquals("com.google.code.guice:guice:1.0", dependencyModel.getCompactNotation());

    // Test reset
    dependencyModel.setVersion("1.1");
    assertEquals("1.1", dependencyModel.version());
    buildModel.resetState();
    dependencyModel = getDependency(buildModel.dependenciesV2());
    assertEquals("1.0", dependencyModel.version());

    // Test Read after write
    dependencyModel.setVersion("1.2");

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    DependenciesModel dependenciesModel = buildModel.dependenciesV2();
    assertNotNull(dependenciesModel);
    dependencyModel = getDependency(dependenciesModel);

    assertEquals("com.google.code.guice:guice:1.2", dependencyModel.getCompactNotation());

    // Test Remove
    dependenciesModel.remove(dependencyModel);
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();
    dependenciesModel = buildModel.dependenciesV2();
    assertNull(dependenciesModel);
  }

  private static ArtifactDependencyModel getDependency(@Nullable DependenciesModel dependenciesModel) {
    assertNotNull(dependenciesModel);
    List<ArtifactDependencyModel> artifactDependencies = dependenciesModel.artifactDependencies(CommonConfigurationNames.COMPILE);
    assertSize(1, artifactDependencies);
    return artifactDependencies.get(0);
  }
}
