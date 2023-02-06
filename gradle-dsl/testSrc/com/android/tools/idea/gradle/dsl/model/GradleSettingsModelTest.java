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
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.api.settings.DependencyResolutionManagementModel;
import com.android.tools.idea.gradle.dsl.api.settings.PluginManagementModel;
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel;
import com.android.tools.idea.gradle.dsl.api.settings.PluginsModel;
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link GradleSettingsModel}.
 */
public class GradleSettingsModelTest extends GradleFileModelTestCase {
  @Test
  public void testIncludedModulePaths() throws Exception {
    writeToSettingsFile(TestFile.INCLUDED_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());
  }

  @Test
  public void testIncludedModulePathsWithDotSeparator() throws Exception {
    writeToBuildFile(TestFile.INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR);
    writeToSettingsFile(TestFile.INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR_SETTINGS);
    Module newModule = writeToNewSubModule("app.ext", "", "");

    ProjectBuildModel projectModel = getProjectBuildModel();
    GradleBuildModel buildModel = projectModel.getModuleBuildModel(newModule);

    assertNotNull(buildModel);
  }

  @Test
  public void testAddAndResetModulePaths() throws Exception {
    writeToSettingsFile(TestFile.ADD_AND_RESET_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.addModulePath("lib1");
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib"), settingsModel.modulePaths());
  }

  @Test
  public void testAddAndApplyModulePaths() throws Exception {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.addModulePath("lib1");
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testAddAndApplyAllModulePaths() throws Exception {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_ALL_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":"), settingsModel.modulePaths());

    settingsModel.addModulePath("app");
    assertEquals("include", ImmutableSet.of(":", ":app"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableSet.of(":", ":app"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableSet.of(":", ":app"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_ALL_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testRemoveAndResetModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REMOVE_AND_RESET_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    assertEquals("include", ImmutableSet.of(":", ":lib"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib"), settingsModel.modulePaths());
  }

  @Test
  public void testRemoveAndApplyModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REMOVE_AND_APPLY_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    assertEquals("include", ImmutableSet.of(":", ":lib"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableSet.of(":", ":lib"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableSet.of(":", ":lib"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.REMOVE_AND_APPLY_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testRemoveAndApplyAllModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REMOVE_AND_APPLY_ALL_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    settingsModel.removeModulePath("lib");
    settingsModel.removeModulePath(":lib1");
    assertEquals("include", ImmutableSet.of(":"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableSet.of(":"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableSet.of(":"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.REMOVE_AND_APPLY_ALL_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testReplaceAndResetModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REPLACE_AND_RESET_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.replaceModulePath("lib", "lib1");
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());
  }

  @Test
  public void testReplaceAndApplyModulePaths() throws Exception {
    writeToSettingsFile(TestFile.REPLACE_AND_APPLY_MODULE_PATHS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.replaceModulePath("lib", "lib1");
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    applyChanges(settingsModel);
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableSet.of(":", ":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    verifyFileContents(mySettingsFile, TestFile.REPLACE_AND_APPLY_MODULE_PATHS_EXPECTED);
  }

  @Test
  public void testGetModuleDirectory() throws Exception {
    writeToSettingsFile(TestFile.GET_MODULE_DIRECTORY);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableSet.of(":", ":app", ":libs", ":libs:mylibrary", ":olibs", ":olibs:mylibrary", ":notamodule:deepmodule"),
                 settingsModel.modulePaths());

    File rootDir = getBaseDirPath(myProject);
    assertEquals(rootDir, settingsModel.moduleDirectory(":"));
    assertEquals(new File(rootDir, "app"), settingsModel.moduleDirectory("app"));
    assertEquals(new File(rootDir, "libs"), settingsModel.moduleDirectory(":libs"));
    assertEquals(new File(rootDir, "xyz/mylibrary"), settingsModel.moduleDirectory(":libs:mylibrary"));
    assertEquals(new File(rootDir, "otherlibs"), settingsModel.moduleDirectory("olibs"));
    assertEquals(new File(rootDir, "otherlibs/mylibrary"), settingsModel.moduleDirectory(":olibs:mylibrary"));
    assertEquals(new File(rootDir, "notamodule/deepmodule"), settingsModel.moduleDirectory(":notamodule:deepmodule"));
  }

  @Test
  public void testGetModuleWithDirectory() throws Exception {
    writeToSettingsFile(TestFile.GET_MODULE_WITH_DIRECTORY);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableSet.of(":", ":app", ":libs", ":libs:mylibrary", ":olibs", ":olibs:mylibrary", ":notamodule:deepmodule"),
                 settingsModel.modulePaths());

    File rootDir = getBaseDirPath(myProject);
    assertEquals(":", settingsModel.moduleWithDirectory(rootDir));
    assertEquals(":app", settingsModel.moduleWithDirectory(new File(rootDir, "app")));
    assertEquals(":libs", settingsModel.moduleWithDirectory(new File(rootDir, "libs")));
    assertEquals(":libs:mylibrary", settingsModel.moduleWithDirectory(new File(rootDir, "xyz/mylibrary")));
    assertEquals(":olibs", settingsModel.moduleWithDirectory(new File(rootDir, "otherlibs")));
    assertEquals(":olibs:mylibrary", settingsModel.moduleWithDirectory(new File(rootDir, "otherlibs/mylibrary")));
    assertEquals(":notamodule:deepmodule", settingsModel.moduleWithDirectory(new File(rootDir, "notamodule/deepmodule")));
  }

  @Test
  public void testGetBuildFile() throws Exception {
    writeToSettingsFile(TestFile.GET_BUILD_FILE);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableSet.of(":", ":app", ":lib", ":olib"), settingsModel.modulePaths());

    File rootDir = getBaseDirPath(myProject);
    assertEquals(new File(rootDir, "build.gradle" + (isGroovy()?"":".kts")), settingsModel.buildFile(""));
    assertEquals(new File(rootDir, "app/build.gradle"), settingsModel.buildFile("app"));
    assertEquals(new File(rootDir, "lib/test.gradle" + (isGroovy()?"":".kts")), settingsModel.buildFile(":lib"));
    assertEquals(new File(rootDir, "otherlibs/xyz/other.gradle" + (isGroovy()?"":".kts")), settingsModel.buildFile(":olib"));
  }

  @Test
  public void testGetParentModule() throws Exception {
    writeToSettingsFile(TestFile.GET_PARENT_MODULE);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertEquals(ImmutableSet.of(":", ":app", ":libs", ":libs:mylibrary", ":olibs", ":olibs:mylibrary", ":notamodule:deepmodule"),
                 settingsModel.modulePaths());

    assertEquals(":", settingsModel.parentModule("app"));
    assertEquals(":", settingsModel.parentModule(":libs"));
    assertEquals(":libs", settingsModel.parentModule("libs:mylibrary"));
    assertEquals(":", settingsModel.parentModule("olibs"));
    assertEquals(":olibs", settingsModel.parentModule(":olibs:mylibrary"));
    assertEquals(":", settingsModel.parentModule(":notamodule:deepmodule"));
  }

  @Test
  public void testSetProjectDir() throws Exception {
    writeToSettingsFile(TestFile.SET_PROJECT_DIR);

    GradleSettingsModel settingsModel = getGradleSettingsModel();

    settingsModel.setModuleDirectory(":app", new File(myProjectBasePath.getPath(), "newAppLocation"));
    applyChanges(settingsModel);

    // Failure currently expected, the writing format and parsing format for this property don't match.
    //File preParseAppLocation = settingsModel.moduleDirectory(":app");
    //assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), preParseAppLocation);

    // Re-parsing should change the property into a readable format.
    settingsModel.reparse();
    File appLocation = settingsModel.moduleDirectory(":app");
    assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), appLocation);

    verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_EXPECTED);
  }

  @Test
  public void testSetProjectDirFromExisting() throws Exception {
    writeToSettingsFile(TestFile.SET_PROJECT_DIR_FROM_EXISTING);
    GradleSettingsModel settingsModel = getGradleSettingsModel();

    settingsModel.setModuleDirectory(":lib", new File(myProjectBasePath.getPath(), "libLocation"));
    applyChanges(settingsModel);

    // Failure currently expected, the writing format and parsing format for this property don't match.
    //File preParseAppLocation = settingsModel.moduleDirectory(":app");
    //assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), preParseAppLocation);

    // Re-parsing should change the property into a readable format.
    settingsModel.reparse();
    File appLocation = settingsModel.moduleDirectory(":lib");
    assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "libLocation"), appLocation);

    verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_FROM_EXISTING_EXPECTED);
  }

  @Test
  public void testSetProjectDirNonRelativePath() throws Exception {
    writeToSettingsFile(TestFile.SET_PROJECT_DIR);

    GradleSettingsModel settingsModel = getGradleSettingsModel();

    settingsModel.setModuleDirectory(":app", new File("/cool/app"));
    applyChanges(settingsModel);

    // Failure currently expected, the writing format and parsing format for this property don't match.
    //File preParseAppLocation = settingsModel.moduleDirectory(":app");
    //assertEquals(new File(settingsModel.getVirtualFile().getParent().getPath(), "newAppLocation"), preParseAppLocation);

    // Re-parsing should change the property into a readable format.
    settingsModel.reparse();
    File appLocation = settingsModel.moduleDirectory(":app");
    File expected = new File("/cool/app").getAbsoluteFile();
    assertEquals(expected, appLocation);
    if (System.getProperty("os.name").startsWith("Windows")) {
      verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_NON_RELATIVE_WINDOWS_EXPECTED);
    }
    else {
      verifyFileContents(mySettingsFile, TestFile.SET_PROJECT_DIR_NON_RELATIVE_EXPECTED);
    }
  }

  @Test
  public void testExistingVariable() throws Exception {
    writeToSettingsFile(TestFile.EXISTING_VARIABLE);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    settingsModel.addModulePath("lib1");
    applyChanges(settingsModel);

    verifyFileContents(mySettingsFile, TestFile.EXISTING_VARIABLE_EXPECTED);
  }

  @Test
  public void testAddIncludeAtTheEnd() throws IOException {
    writeToSettingsFile(TestFile.ADD_INCLUDE_AT_THE_END);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    settingsModel.addModulePath("libs:mylibrary");

    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_INCLUDE_AT_THE_END_EXPECTED);
  }

  @Test
  public void testParseDependencyResolutionManagement() throws IOException {
    writeToSettingsFile(TestFile.PARSE_DEPENDENCY_RESOLUTION_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    DependencyResolutionManagementModel dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement();
    RepositoriesModel repositoriesModel = dependencyResolutionManagementModel.repositories();

    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertSize(2, repositories);
    assertEquals("Google", repositories.get(0).name().forceString());
    assertEquals("BintrayJCenter2", repositories.get(1).name().forceString());
  }

  @Test
  public void testAddAndApplyDependencyResolutionManagement() throws IOException {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    DependencyResolutionManagementModel dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement();
    RepositoriesModel repositoriesModel = dependencyResolutionManagementModel.repositories();

    repositoriesModel.addGoogleMavenRepository();
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT_EXPECTED);
  }

  @Test
  public void testEditAndApplyDependencyResolutionManagement() throws IOException {
    writeToSettingsFile(TestFile.EDIT_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    DependencyResolutionManagementModel dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement();
    RepositoriesModel repositoriesModel = dependencyResolutionManagementModel.repositories();

    repositoriesModel.removeRepository(repositoriesModel.repositories().get(0));
    repositoriesModel.addGoogleMavenRepository();
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.EDIT_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT_EXPECTED);
  }

  @Test
  public void testParsePluginManagement() throws IOException {
    writeToSettingsFile(TestFile.PARSE_PLUGIN_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    PluginManagementModel pluginManagementModel = settingsModel.pluginManagement();
    RepositoriesModel repositoriesModel = pluginManagementModel.repositories();
    PluginsModel pluginsModel = pluginManagementModel.plugins();

    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertSize(2, repositories);
    assertEquals("Google", repositories.get(0).name().forceString());
    assertEquals("BintrayJCenter2", repositories.get(1).name().forceString());

    List<PluginModel> plugins = pluginsModel.plugins();
    assertSize(2, plugins);
    assertEmpty(pluginsModel.appliedPlugins());
    assertEquals("com.android.application", plugins.get(0).name().forceString());
    assertEquals("4.2.0", plugins.get(0).version().forceString());
    assertMissingProperty(plugins.get(0).apply());
    assertEquals("org.jetbrains.kotlin.android", plugins.get(1).name().forceString());
    assertEquals("1.4.10", plugins.get(1).version().forceString());
    assertFalse(plugins.get(1).apply().toBoolean());
  }

  @Test
  public void testAddAndApplyPluginManagement() throws IOException {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_PLUGIN_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    PluginManagementModel pluginManagementModel = settingsModel.pluginManagement();
    PluginsModel pluginsModel = pluginManagementModel.plugins();
    RepositoriesModel repositoriesModel = pluginManagementModel.repositories();

    repositoriesModel.addGoogleMavenRepository();
    pluginsModel.applyPlugin("com.android.application", "7.0.0");
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_PLUGIN_MANAGEMENT_EXPECTED);
  }

  @Test
  public void testAddAndApplyPluginManagementThreeArguments() throws IOException {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_PLUGIN_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    PluginManagementModel pluginManagementModel = settingsModel.pluginManagement();
    PluginsModel pluginsModel = pluginManagementModel.plugins();
    RepositoriesModel repositoriesModel = pluginManagementModel.repositories();

    repositoriesModel.addGoogleMavenRepository();
    pluginsModel.applyPlugin("com.android.application", "7.0.0", false);
    assertEmpty(pluginsModel.appliedPlugins());
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_PLUGIN_MANAGEMENT_THREE_ARGUMENTS_EXPECTED);
  }

  @Test
  public void testEditAndApplyPluginManagement() throws IOException {
    writeToSettingsFile(TestFile.EDIT_AND_APPLY_PLUGIN_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    PluginManagementModel pluginManagementModel = settingsModel.pluginManagement();
    PluginsModel pluginsModel = pluginManagementModel.plugins();
    RepositoriesModel repositoriesModel = pluginManagementModel.repositories();

    pluginsModel.removePlugin("com.android.application");
    pluginsModel.applyPlugin("com.android.library", "7.0.0");
    assertEmpty(pluginsModel.appliedPlugins());
    repositoriesModel.removeRepository(repositoriesModel.repositories().get(0));
    repositoriesModel.addGoogleMavenRepository();
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.EDIT_AND_APPLY_PLUGIN_MANAGEMENT_EXPECTED);
  }

  @Test
  public void testEditAndApplyPluginManagementThreeArguments() throws IOException {
    writeToSettingsFile(TestFile.EDIT_AND_APPLY_PLUGIN_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    PluginManagementModel pluginManagementModel = settingsModel.pluginManagement();
    PluginsModel pluginsModel = pluginManagementModel.plugins();
    RepositoriesModel repositoriesModel = pluginManagementModel.repositories();

    pluginsModel.removePlugin("com.android.application");
    pluginsModel.applyPlugin("com.android.library", "7.0.0", false);
    assertEmpty(pluginsModel.appliedPlugins());
    repositoriesModel.removeRepository(repositoriesModel.repositories().get(0));
    repositoriesModel.addGoogleMavenRepository();
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.EDIT_AND_APPLY_PLUGIN_MANAGEMENT_THREE_ARGUMENTS_EXPECTED);
  }

  @Test
  public void testParsePluginsBlockInSettings() throws IOException {
    writeToSettingsFile(TestFile.PARSE_PLUGINS_BLOCK);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    PluginsModel pluginsModel = settingsModel.plugins();
    assertEquals("com.android.settings", pluginsModel.plugins().get(0).name().forceString());
    assertEquals("7.4.0", pluginsModel.plugins().get(0).version().forceString());
  }

  @Test
  public void testAddPluginsBlock() throws IOException {
    writeToSettingsFile(TestFile.PARSE_DEPENDENCY_RESOLUTION_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    settingsModel.plugins().applyPlugin("com.android.settings", "7.4.0");
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_PLUGINS_BLOCK_EXPECTED);

  }

  @Test
  public void testAddPluginsBlockWithPluginManagement() throws IOException {
    writeToSettingsFile(TestFile.EDIT_AND_APPLY_PLUGIN_MANAGEMENT);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    settingsModel.plugins().applyPlugin("com.android.settings", "7.4.0");
    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_PLUGINS_BLOCK_WITH_PLUGIN_MANAGEMENT_EXPECTED);
  }

  @Test
  public void testParseVersionCatalogs() throws IOException {
    writeToSettingsFile(TestFile.PARSE_VERSION_CATALOGS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    DependencyResolutionManagementModel dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement();

    List<VersionCatalogModel> versionCatalogs = dependencyResolutionManagementModel.versionCatalogs();
    assertSize(3, versionCatalogs);
    assertEquals("libs", versionCatalogs.get(0).getName());
    assertEquals("gradle/libs.versions.toml", versionCatalogs.get(0).from().toString());
    assertEquals("foo", versionCatalogs.get(1).getName());
    assertEquals("gradle/foo.versions.toml", versionCatalogs.get(1).from().toString());
    assertEquals("bar", versionCatalogs.get(2).getName());
    assertMissingProperty(versionCatalogs.get(2).from());
  }

  @Test
  public void testAddVersionCatalogs() throws IOException {
    writeToSettingsFile(TestFile.ADD_VERSION_CATALOGS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    DependencyResolutionManagementModel dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement();

    VersionCatalogModel foo = dependencyResolutionManagementModel.addVersionCatalog("foo");
    foo.from().setValue("gradle/foo.versions.toml");
    VersionCatalogModel bar = dependencyResolutionManagementModel.addVersionCatalog("bar");

    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_VERSION_CATALOGS_EXPECTED);
  }

  @Test
  public void testRemoveVersionCatalogs() throws IOException {
    writeToSettingsFile(TestFile.REMOVE_VERSION_CATALOGS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    DependencyResolutionManagementModel dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement();

    dependencyResolutionManagementModel.removeVersionCatalog("foo");
    dependencyResolutionManagementModel.removeVersionCatalog("bar");

    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, "");
  }

  @Test
  public void testEditVersionCatalogs() throws IOException {
    writeToSettingsFile(TestFile.EDIT_VERSION_CATALOGS);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    DependencyResolutionManagementModel dependencyResolutionManagementModel = settingsModel.dependencyResolutionManagement();

    VersionCatalogModel libs = dependencyResolutionManagementModel.versionCatalogs().get(0);
    libs.from().setValue("gradle/new-libs.versions.toml");
    VersionCatalogModel foo = dependencyResolutionManagementModel.versionCatalogs().get(1);
    foo.from().delete();
    VersionCatalogModel bar = dependencyResolutionManagementModel.versionCatalogs().get(2);
    bar.from().setValue("gradle/bar.versions.toml");

    applyChanges(settingsModel);
    verifyFileContents(mySettingsFile, TestFile.EDIT_VERSION_CATALOGS_EXPECTED);
  }

  @Test
  public void testAddAndApplyPluginsByReference() throws IOException {
    writeToSettingsFile(TestFile.ADD_AND_APPLY_PLUGIN_MANAGEMENT);
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG);
    ProjectBuildModel projectModel = getProjectBuildModel();
    GradleSettingsModel settingsModel = projectModel.getProjectSettingsModel();
    GradlePropertyModel foo = projectModel.getVersionCatalogsModel().plugins("libs").findProperty("foo");
    GradlePropertyModel bar = projectModel.getVersionCatalogsModel().plugins("libs").findProperty("bar");
    PluginsBlockModel pmpModel = settingsModel.pluginManagement().plugins();
    pmpModel.applyPlugin(new ReferenceTo(foo, pmpModel), null);
    pmpModel.applyPlugin(new ReferenceTo(bar, pmpModel), true);
    PluginsBlockModel pModel = settingsModel.plugins();
    pModel.applyPlugin(new ReferenceTo(foo, pmpModel), false);
    pModel.applyPlugin(new ReferenceTo(bar, pmpModel), null);

    applyChanges(projectModel);
    verifyFileContents(mySettingsFile, TestFile.ADD_AND_APPLY_PLUGINS_BY_REFERENCE_EXPECTED);
  }

  private void applyChanges(@NotNull final GradleSettingsModel settingsModel) {
    runWriteCommandAction(myProject, () -> settingsModel.applyChanges());
    assertFalse(settingsModel.isModified());
  }

  enum TestFile implements TestFileName {
    INCLUDED_MODULE_PATHS("includedModulePaths"),
    INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR("includedModulePathsWithDotSeparator"),
    INCLUDED_MODULE_PATHS_WITH_DOT_SEPARATOR_SETTINGS("includedModulePathsWithDotSeparatorSettings"),
    ADD_AND_RESET_MODULE_PATHS("addAndResetModulePaths"),
    ADD_AND_APPLY_MODULE_PATHS("addAndApplyModulePaths"),
    ADD_AND_APPLY_MODULE_PATHS_EXPECTED("addAndApplyModulePathsExpected"),
    ADD_AND_APPLY_ALL_MODULE_PATHS("addAndApplyAllModulePaths"),
    ADD_AND_APPLY_ALL_MODULE_PATHS_EXPECTED("addAndApplyAllModulePathsExpected"),
    REMOVE_AND_RESET_MODULE_PATHS("removeAndResetModulePaths"),
    REMOVE_AND_APPLY_MODULE_PATHS("removeAndApplyModulePaths"),
    REMOVE_AND_APPLY_MODULE_PATHS_EXPECTED("removeAndApplyModulePathsExpected"),
    REMOVE_AND_APPLY_ALL_MODULE_PATHS("removeAndApplyAllModulePaths"),
    REMOVE_AND_APPLY_ALL_MODULE_PATHS_EXPECTED("removeAndApplyAllModulePathsExpected"),
    REPLACE_AND_RESET_MODULE_PATHS("replaceAndResetModulePaths"),
    REPLACE_AND_APPLY_MODULE_PATHS("replaceAndApplyModulePaths"),
    REPLACE_AND_APPLY_MODULE_PATHS_EXPECTED("replaceAndApplyModulePathsExpected"),
    GET_MODULE_DIRECTORY("getModuleDirectory"),
    GET_MODULE_WITH_DIRECTORY("getModuleWithDirectory"),
    GET_BUILD_FILE("getBuildFile"),
    GET_PARENT_MODULE("getParentModule"),
    EXISTING_VARIABLE("existingVariable"),
    EXISTING_VARIABLE_EXPECTED("existingVariableExpected"),
    SET_PROJECT_DIR("setProjectDir"),
    SET_PROJECT_DIR_EXPECTED("setProjectDirExpected"),
    SET_PROJECT_DIR_FROM_EXISTING("setProjectDirFromExisting"),
    SET_PROJECT_DIR_FROM_EXISTING_EXPECTED("setProjectDirFromExistingExpected"),
    SET_PROJECT_DIR_NON_RELATIVE_EXPECTED("setProjectDirNonRelativeExpected"),
    SET_PROJECT_DIR_NON_RELATIVE_WINDOWS_EXPECTED("setProjectDirNonRelativeWindowsExpected"),
    ADD_INCLUDE_AT_THE_END("addIncludeAtTheEnd"),
    ADD_INCLUDE_AT_THE_END_EXPECTED("addIncludeAtTheEndExpected"),
    PARSE_DEPENDENCY_RESOLUTION_MANAGEMENT("parseDependencyResolutionManagement"),
    ADD_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT("addAndApplyDependencyResolutionManagement"),
    ADD_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT_EXPECTED("addAndApplyDependencyResolutionManagementExpected"),
    EDIT_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT("editAndApplyDependencyResolutionManagement"),
    EDIT_AND_APPLY_DEPENDENCY_RESOLUTION_MANAGEMENT_EXPECTED("editAndApplyDependencyResolutionManagementExpected"),
    PARSE_PLUGIN_MANAGEMENT("parsePluginManagement"),
    ADD_AND_APPLY_PLUGIN_MANAGEMENT("addAndApplyPluginManagement"),
    ADD_AND_APPLY_PLUGIN_MANAGEMENT_EXPECTED("addAndApplyPluginManagementExpected"),
    ADD_AND_APPLY_PLUGIN_MANAGEMENT_THREE_ARGUMENTS_EXPECTED("addAndApplyPluginManagementThreeArgumentsExpected"),
    ADD_AND_APPLY_PLUGINS_BY_REFERENCE_EXPECTED("addAndApplyPluginsByReferenceExpected"),
    EDIT_AND_APPLY_PLUGIN_MANAGEMENT("editAndApplyPluginManagement"),
    EDIT_AND_APPLY_PLUGIN_MANAGEMENT_EXPECTED("editAndApplyPluginManagementExpected"),
    EDIT_AND_APPLY_PLUGIN_MANAGEMENT_THREE_ARGUMENTS_EXPECTED("editAndApplyPluginManagementThreeArgumentsExpected"),
    PARSE_PLUGINS_BLOCK("parsePluginsBlock"),
    ADD_PLUGINS_BLOCK_EXPECTED("addPluginsBlockExpected"),
    ADD_PLUGINS_BLOCK_WITH_PLUGIN_MANAGEMENT_EXPECTED("addPluginsBlockWithPluginManagementExpected"),
    PARSE_VERSION_CATALOGS("parseVersionCatalogs"),
    ADD_VERSION_CATALOGS("addVersionCatalogs"),
    ADD_VERSION_CATALOGS_EXPECTED("addVersionCatalogsExpected"),
    EDIT_VERSION_CATALOGS("editVersionCatalogs"),
    EDIT_VERSION_CATALOGS_EXPECTED("editVersionCatalogsExpected"),
    REMOVE_VERSION_CATALOGS("removeVersionCatalogs"),
    VERSION_CATALOG("versionCatalog.toml"),

    ;
    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/gradleSettingsModel/" + path, extension);
    }
  }
}
