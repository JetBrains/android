/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getDefaultPhysicalPathFromGradlePath;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.contains;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.io.Files.write;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.util.PathUtil.toSystemIndependentName;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link GradleModuleImporter#importModules(Object, Map, Project, GradleSyncListener)}.
 */
@SuppressWarnings("JUnitTestCaseWithNoTests") // Named differently, didn't want to do too much unnecessary setups
public final class GradleModuleImportTest extends HeavyPlatformTestCase {
  public static final String MODULE_NAME = "guadeloupe";
  public static final String SAMPLE_PROJECT_PATH = "samples/sample1";
  public static final String BUILD_GRADLE_TEMPLATE = "apply plugin: 'com.android.library'\n\n" +
                                                     "dependencies {\n" +
                                                     "    implementation 'com.android.support:support-v4:13.0.+'\n" +
                                                     "    %s\n" +
                                                     "}\n\n" +
                                                     "android {\n" +
                                                     "    compileSdkVersion 19\n" +
                                                     "    buildToolsVersion \"19\"\n\n" +
                                                     "    defaultConfig {\n" +
                                                     "        minSdkVersion 8\n" +
                                                     "        targetSdkVersion 19\n" +
                                                     "    }\n\n" +
                                                     "    sourceSets {\n" +
                                                     "        main {\n" +
                                                     "            manifest.srcFile 'AndroidManifest.xml'\n" +
                                                     "            java.srcDirs = ['src']\n" +
                                                     "            res.srcDirs = ['res']\n" +
                                                     "        }\n" +
                                                     "    }\n" +
                                                     "}\n";
  private final static Function<String, String> pathToModuleName = GradleModuleImportTest::pathToGradleName;
  private File dir;

  public static VirtualFile createGradleProjectToImport(File dir, String name, String... requiredProjects) throws IOException {
    File moduleDir = new File(dir, name);
    if (!moduleDir.mkdirs()) {
      throw new IllegalStateException("Unable to create module");
    }
    Iterable<String> projectDependencies =
      Arrays.stream(requiredProjects).map(input -> String.format("\timplementation project('%s')", pathToGradleName(input)))
        .collect(Collectors.toList());
    String buildGradle = String.format(BUILD_GRADLE_TEMPLATE, Joiner.on("\n").join(projectDependencies));
    write(buildGradle, new File(moduleDir, SdkConstants.FN_BUILD_GRADLE), Charset.defaultCharset());
    VirtualFile moduleFile = findFileByIoFile(moduleDir, true);
    if (moduleFile == null) {
      throw new IllegalStateException("Cannot get virtual file for module we just created");
    }
    else {
      return moduleFile;
    }
  }

  private static String module(int moduleNumber) {
    return MODULE_NAME + moduleNumber;
  }

  private static Map<String, String> projectsWithDefaultLocations(final String... paths) {
    Iterable<String> names = Arrays.stream(paths).map(pathToModuleName).collect(Collectors.toList());
    return Maps.toMap(names, Functions.constant(""));
  }

  private static String pathToGradleName(String input) {
    return ":" + input.replaceAll("/", SdkConstants.GRADLE_PATH_SEPARATOR);
  }

  private static VirtualFile configureTopLevelProject(File projectRoot,
                                                      Iterable<String> allModules,
                                                      Iterable<String> customLocationStatements) throws IOException {
    StringBuilder settingsGradle = new StringBuilder("include '");
    Joiner.on("', '").appendTo(settingsGradle, allModules).append("'\n");
    Joiner.on("\n").appendTo(settingsGradle, customLocationStatements).append("\n");

    write(settingsGradle.toString(), new File(projectRoot, SdkConstants.FN_SETTINGS_GRADLE), Charset.defaultCharset());
    VirtualFile vDir = findFileByIoFile(projectRoot, true);
    assert vDir != null;
    System.out.printf("Multi-project root: %s\n", vDir.getPath());
    return vDir;
  }

  @NotNull
  private VirtualFile createProjectWithSubprojects(Map<String, String> modules, String... nonExistingReferencedModules) throws IOException {
    Collection<String> customLocationStatements = new LinkedList<>();

    for (Map.Entry<String, String> module : modules.entrySet()) {
      String path = module.getValue();
      if (isNullOrEmpty(path)) {
        path = toSystemIndependentName(getDefaultPhysicalPathFromGradlePath(module.getKey()));
      }
      else {
        customLocationStatements.add(String.format("project('%s').projectDir = new File('%s')", module.getKey(), path));
      }
      createGradleProjectToImport(dir, path);
    }
    Iterable<String> allModules = concat(modules.keySet(), Arrays.stream(nonExistingReferencedModules).map(pathToModuleName)
      .collect(Collectors.toList()));
    return configureTopLevelProject(dir, allModules, customLocationStatements);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Project project = getProject();
    dir = getBaseDirPath(project);
    IdeSdks.removeJdksOn(getTestRootDisposable());
  }

  /**
   * Test importing simple module into even simpler project
   */
  public void testImportSimpleGradleProject() throws Exception {
    VirtualFile moduleRoot = createGradleProjectToImport(dir, MODULE_NAME);
    GradleModuleImporter.importModules(this, Collections.singletonMap(moduleRoot.getName(), moduleRoot), getProject(), null);
    assertModuleImported(getProject(), MODULE_NAME, moduleRoot);
  }

  /**
   * Test importing a root project that has subprojects
   */
  public void testImportSubprojects() throws IOException {
    String[] paths = {module(1), module(2), SAMPLE_PROJECT_PATH};

    VirtualFile projectRoot = createProjectWithSubprojects(projectsWithDefaultLocations(paths));
    Map<String, VirtualFile> toImport = moduleListToMap(GradleModuleImporter.getRelatedProjects(projectRoot, getProject()));
    assertEquals(paths.length, toImport.size());
    for (String path : paths) {
      assertEquals(projectRoot.findFileByRelativePath(path), toImport.get(pathToGradleName(path)));
    }

    GradleModuleImporter.importModules(this, toImport, getProject(), null);

    for (String path : paths) {
      VirtualFile moduleRoot = projectRoot.findFileByRelativePath(path);
      assertNotNull(String.format("Module was not imported into %s\n", projectRoot.getPath() + "/" + path), moduleRoot);
      assertModuleImported(getProject(), path, moduleRoot);
    }

    System.out.println();
  }

  /**
   * Missing sub-module will be on the list but with a <code>null</code> path. It is up to client code to decide what to do with it.
   */
  public void testImportSubProjectsWithMissingSubModule() throws IOException {
    VirtualFile projectRoot = createProjectWithSubprojects(projectsWithDefaultLocations(module(1)), module(2));
    Map<String, VirtualFile> toImport = moduleListToMap(GradleModuleImporter.getRelatedProjects(projectRoot, getProject()));
    assertEquals(2, toImport.size());
    assertModuleRequiredButNotFound(module(2), toImport);

    try {
      GradleModuleImporter.importModules(this, toImport, getProject(), null);
      fail();
    }
    catch (IOException e) {
      // Expected
    }
  }

  /**
   * Verify discovery of projects with non-default locations
   */
  public void testImportSubProjectWithCustomLocation() throws IOException {
    VirtualFile projectRoot =
      createProjectWithSubprojects(Collections.singletonMap(pathToGradleName(SAMPLE_PROJECT_PATH), SAMPLE_PROJECT_PATH));
    Map<String, VirtualFile> subProjects = moduleListToMap(GradleModuleImporter.getRelatedProjects(projectRoot, getProject()));
    assertEquals(1, subProjects.size());
    VirtualFile moduleLocation = projectRoot.findFileByRelativePath(SAMPLE_PROJECT_PATH);
    assert moduleLocation != null;
    assertEquals(moduleLocation, subProjects.get(pathToGradleName(SAMPLE_PROJECT_PATH)));

    GradleModuleImporter.importModules(this, subProjects, getProject(), null);
    assertModuleImported(getProject(), SAMPLE_PROJECT_PATH, moduleLocation);
  }

  private static Map<String, VirtualFile> moduleListToMap(Set<ModuleToImport> projects) {
    HashMap<String, VirtualFile> map = Maps.newHashMapWithExpectedSize(projects.size());
    for (ModuleToImport project : projects) {
      map.put(project.name, project.location);
    }
    return map;
  }

  /**
   * Verify basic case of importing a project that has source dependency
   */
  public void testRequiredProjects() throws IOException {
    VirtualFile project1 = createGradleProjectToImport(dir, module(1));
    VirtualFile project2 = createGradleProjectToImport(dir, module(2), module(1));
    configureTopLevelProject(dir, Arrays.asList(module(1), module(2)), Collections.emptySet());

    Map<String, VirtualFile> projects = moduleListToMap(GradleModuleImporter.getRelatedProjects(project2, getProject()));
    assertEquals(2, projects.size());
    assertEquals(project1, projects.get(pathToGradleName(module(1))));
    assertEquals(project2, projects.get(pathToGradleName(module(2))));
  }

  /**
   * Test importing a project has source dependency but when that dependency directory is missing
   */
  public void testMissingRequiredProjects() throws IOException {
    VirtualFile project2 = createGradleProjectToImport(dir, module(2), module(1));
    configureTopLevelProject(dir, Arrays.asList(module(1), module(2)), Collections.emptySet());

    Map<String, VirtualFile> projects = moduleListToMap(GradleModuleImporter.getRelatedProjects(project2, getProject()));
    assertEquals(2, projects.size());
    assertModuleRequiredButNotFound(module(1), projects);
    assertEquals(project2, projects.get(pathToGradleName(module(2))));
  }

  /**
   * Test a project that requires another project but when no settings.gradle was found in the parent folder.
   */
  public void testMissingEnclosingProject() throws IOException {
    VirtualFile module = createGradleProjectToImport(dir, module(1), module(2));

    Map<String, VirtualFile> projects = moduleListToMap(GradleModuleImporter.getRelatedProjects(module, getProject()));
    assertEquals(2, projects.size());
    assertModuleRequiredButNotFound(module(2), projects);
    assertEquals(module, projects.get(pathToGradleName(module(1))));
  }

  /**
   * Make sure source dependencies are picked recursively
   */
  public void testTransitiveDependencies() throws IOException {
    VirtualFile project1 = createGradleProjectToImport(dir, module(1));
    VirtualFile project2 = createGradleProjectToImport(dir, module(2), module(1));
    VirtualFile project3 = createGradleProjectToImport(dir, module(3), module(2));
    configureTopLevelProject(dir, Arrays.asList(module(1), module(2), module(3)), Collections.emptySet());

    Map<String, VirtualFile> projects = moduleListToMap(GradleModuleImporter.getRelatedProjects(project3, getProject()));
    assertEquals(3, projects.size());
    assertEquals(project1, projects.get(pathToGradleName(module(1))));
    assertEquals(project2, projects.get(pathToGradleName(module(2))));
    assertEquals(project3, projects.get(pathToGradleName(module(3))));
  }

  /**
   * Make sure source dependencies are picked recursively
   */
  public void testCircularDependencies() throws IOException {
    VirtualFile project1 = createGradleProjectToImport(dir, module(1), module(3));
    VirtualFile project2 = createGradleProjectToImport(dir, module(2), module(1));
    VirtualFile project3 = createGradleProjectToImport(dir, module(3), module(2));
    configureTopLevelProject(dir, Arrays.asList(module(1), module(2), module(3)), Collections.<String>emptySet());

    Map<String, VirtualFile> projects = moduleListToMap(GradleModuleImporter.getRelatedProjects(project3, getProject()));
    assertEquals(3, projects.size());
    assertEquals(project1, projects.get(pathToGradleName(module(1))));
    assertEquals(project2, projects.get(pathToGradleName(module(2))));
    assertEquals(project3, projects.get(pathToGradleName(module(3))));
  }

  private static void assertModuleInSettingsFile(Project project, String name) {
    GradleSettingsModel settingsModel = ProjectBuildModel.get(project).getProjectSettingsModel();
    assertNotNull("Missing " + SdkConstants.FN_SETTINGS_GRADLE, settingsModel);
    Iterable<String> modules = settingsModel.modulePaths();
    if (!contains(modules, name)) {
      fail(String.format("Subproject %s is not in %s. Found subprojects: %s", name, SdkConstants.FN_SETTINGS_GRADLE,
                         Joiner.on(", ").join(modules)));
    }
  }

  private static void assertModuleImported(@NotNull Project project, @NotNull String relativePath, @NotNull VirtualFile moduleRoot) {
    assertNotNull("Module sources were not copied", PlatformTestUtil.getOrCreateProjectBaseDir(project).findFileByRelativePath(relativePath));
    final VirtualFile[] moduleChildren = moduleRoot.getChildren();
    assertNoFilesAdded(moduleChildren);
    assertEquals(SdkConstants.FN_BUILD_GRADLE, moduleChildren[0].getName());
    assertModuleInSettingsFile(project, pathToGradleName(relativePath));
  }

  private static void assertNoFilesAdded(VirtualFile[] moduleChildren) {
    if (moduleChildren.length != 1) {
      StringBuilder failure = new StringBuilder("Files were altered in the source directory:");
      Joiner.on(", ").appendTo(failure, transform(Arrays.asList(moduleChildren), VirtualFile::getName));
      fail(failure.toString());
    }
  }

  private static void assertModuleRequiredButNotFound(String modulePath, Map<String, VirtualFile> projects) {
    String moduleName = pathToGradleName(modulePath);
    assertTrue(String.format("Project %s should be known but path not detected", modulePath),
               projects.containsKey(moduleName) && projects.get(moduleName) == null);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }
}