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
package com.android.tools.idea.testartifacts.scopes;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.JavaArtifact;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.util.PathsList;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.sdklib.IAndroidTarget.ANDROID_JAR;
import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.intellij.openapi.util.io.FileUtil.pathsEqual;

/**
 * Implementation of {@link JUnitPatcher} that removes android.jar from the class path. It's only applicable to
 * JUnit run configurations if the selected test artifact is "unit tests". In this case, the mockable android.jar is already in the
 * dependencies (taken from the model).
 */
public class AndroidJunitPatcher extends JUnitPatcher {
  @Override
  public void patchJavaParameters(@Nullable Module module, @NotNull JavaParameters javaParameters) {
    // Only patch if the project is a Gradle project.
    if (module == null || !GradleProjectInfo.getInstance(module.getProject()).isBuildWithGradle()) {
      return;
    }

    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel == null) {
      // Add resource folders if Java module and for module dependencies
      addFoldersToClasspath(module, null, javaParameters.getClassPath());
      return;
    }

    // Modify the class path only if we're dealing with the unit test artifact.
    JavaArtifact testArtifact = androidModel.getSelectedVariant().getUnitTestArtifact();
    if (testArtifact == null) {
      return;
    }

    PathsList classPath = javaParameters.getClassPath();

    TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(module);
    if (testScopes == null) {
      return;
    }

    // Filter the library / module dependencies that are in android test
    FileRootSearchScope excludeScope = testScopes.getUnitTestExcludeScope();
    // There is potential performance if we just call remove for all excluded items because every random remove operation has linear
    // complexity. TODO change the {@code PathList} API.
    for (String path : classPath.getPathList()) {
      if (excludeScope.accept(new File(path))) {
        classPath.remove(path);
      }
    }

    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      return;
    }

    String originalClassPath = classPath.getPathsString();
    try {
      replaceAndroidJarWithMockableJar(classPath, platform, testArtifact);
      addFoldersToClasspath(module, testArtifact, classPath);
    }
    catch (RuntimeException e) {
      throw new RuntimeException(String.format("Error patching the JUnit class path. Original class path:%n%s", originalClassPath), e);
    }
  }

  /**
   * Removes real android.jar from the classpath and puts the mockable one at the end.
   */
  private static void replaceAndroidJarWithMockableJar(@NotNull PathsList classPath,
                                                       @NotNull AndroidPlatform platform,
                                                       @NotNull JavaArtifact artifact) {
    String androidJarPath = platform.getTarget().getPath(ANDROID_JAR);
    for (String entry : classPath.getPathList()) {
      if (pathsEqual(androidJarPath, entry)) {
        classPath.remove(entry);
      }
    }

    // Move the mockable android jar to the end. This is to make sure "empty" classes from android.jar don't end up shadowing real
    // classes needed by the testing code (e.g. XML/JSON related). Since mockable jars were introduced in 1.1, they were put in the model
    // as dependencies, which means a module which depends on Android libraries with different  will end up with more than one mockable jar
    // in the classpath.
    List<String> mockableJars = new ArrayList<>();
    for (String path : classPath.getPathList()) {
      if (toSystemDependentPath(path).getName().startsWith("mockable-")) {
        // PathsList stores strings - use the one that's actually stored there.
        mockableJars.add(path);
      }
    }

    // Remove all mockable android.jars.
    for (String mockableJar : mockableJars) {
      classPath.remove(mockableJar);
    }

    File mockableJar = artifact.getMockablePlatformJar();

    if (mockableJar != null) {
      classPath.addTail(mockableJar.getPath());
    }
    else {
      // We're dealing with an old plugin, that puts the mockable jar in the dependencies. Just put the matching android.jar at the end of
      // the classpath.
      for (String mockableJarPath : mockableJars) {
        if (mockableJarPath.endsWith("-" + platform.getApiLevel() + DOT_JAR)) {
          classPath.addTail(mockableJarPath);
          return;
        }
      }
    }
  }

  /**
   * Puts additional necessary folders for the selected variant of every module on the classpath.
   *
   * <p>The problem we're solving here is that CompilerModuleExtension supports only one directory for "compiler output". When IJ compiles
   * Java projects, it copies resources and Kotlin classes to the output classes dir. This is something Gradle doesn't do, so we need to add
   * these directories to the classpath here.
   *
   * <p>We need to do this for every project dependency as well, since we're using classes and resources directories of these directly.
   *
   * @see <a href="http://b.android.com/172409">Bug 172409</a>
   * @see <a href="http://b.android.com/172409">Bug 172409</a>
   */
  private static void addFoldersToClasspath(@NotNull Module module,
                                            @Nullable JavaArtifact testArtifact,
                                            @NotNull PathsList classPath) {
    CompilerManager compilerManager = CompilerManager.getInstance(module.getProject());
    CompileScope scope = compilerManager.createModulesCompileScope(new Module[]{module}, true, true);

    if (testArtifact != null) {
      classPath.add(testArtifact.getJavaResourcesFolder());
      for (File additionalTestClasses : testArtifact.getAdditionalClassesFolders()) {
        classPath.add(additionalTestClasses);
      }
    }

    FileRootSearchScope excludeScope = null;
    TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(module);
    if (testScopes != null) {
      excludeScope = testScopes.getUnitTestExcludeScope();
    }

    for (Module affectedModule : scope.getAffectedModules()) {
      AndroidModuleModel affectedAndroidModel = AndroidModuleModel.get(affectedModule);
      if (affectedAndroidModel != null) {
        AndroidArtifact mainArtifact = affectedAndroidModel.getMainArtifact();
        addToClasspath(mainArtifact.getJavaResourcesFolder(), classPath, excludeScope);
        for (File additionalClassesFolder : mainArtifact.getAdditionalClassesFolders()) {
          addToClasspath(additionalClassesFolder, classPath, excludeScope);
        }
      }

      // Adds resources from java modules to the classpath (see b/37137712)
      JavaFacet javaFacet = JavaFacet.getInstance(affectedModule);
      if (javaFacet != null) {
        JavaModuleModel javaModel = javaFacet.getJavaModuleModel();
        if (javaModel == null) {
          continue;
        }

        ExtIdeaCompilerOutput output = javaModel.getCompilerOutput();
        File javaTestResources = output == null ? null : output.getTestResourcesDir();
        if (javaTestResources != null) {
          addToClasspath(javaTestResources, classPath, excludeScope);
        }
        File javaMainResources = output == null ? null : output.getMainResourcesDir();
        if (javaMainResources != null) {
          addToClasspath(javaMainResources, classPath, excludeScope);
        }

        if (javaModel.getBuildFolderPath() != null) {
          File kotlinClasses = javaModel.getBuildFolderPath().toPath().resolve("classes").resolve("kotlin").toFile();

          if (kotlinClasses.exists()) {
            // It looks like standard Gradle-4.0-style output directories are used. We add Kotlin equivalents speculatively, since we don't
            // yet have a way of passing the data all the way from Gradle to here.
            addToClasspath(new File(kotlinClasses, "main"), classPath, excludeScope);
            addToClasspath(new File(kotlinClasses, "test"), classPath, excludeScope);
          }
        }
      }
    }
  }

  private static void addToClasspath(@NotNull File folder, @NotNull PathsList classPath, @Nullable FileRootSearchScope excludeScope) {
    if (excludeScope != null && excludeScope.accept(folder)) {
      return;
    }
    classPath.add(folder);
  }
}
