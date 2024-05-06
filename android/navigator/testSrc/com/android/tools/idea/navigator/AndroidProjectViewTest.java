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
package com.android.tools.idea.navigator;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;

import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode;
import com.android.tools.idea.navigator.nodes.android.BuildScriptTreeStructureProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.tools.idea.testing.TestProjectPaths;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectViewState;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import org.jetbrains.annotations.NotNull;

// TODO: Test available actions for each node!
public class AndroidProjectViewTest extends AndroidGradleTestCase {
  private AndroidProjectViewPane myPane;

  public void testGeneratedSourceFiles_lightClasses() throws Exception {
    loadSimpleApplication();

    // Create BuildConfig.java in one of the generated source folders.
    Module appModule = TestModuleUtil.findAppModule(getProject());
    GradleAndroidModel androidModel = GradleAndroidModel.get(appModule);
    assertNotNull(androidModel);
    Collection<File> generatedFolders = androidModel.getMainArtifact().getGeneratedSourceFolders();
    assertThat(generatedFolders).isNotEmpty();

    File buildConfigFolder = generatedFolders.stream().filter(f -> f.getPath().contains("buildConfig")).findFirst().orElse(null);
    assertNotNull(buildConfigFolder);
    writeToFile(new File(buildConfigFolder, join("com", "application", "BuildConfig.java")),
                "package com.application; public final class BuildConfig {}");

    refreshProjectFiles();
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(getProject());
    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Set<List<String>> allNodes = getAllNodes(structure);
    assertThat(allNodes).contains(Arrays.asList("app (Android)", "java (generated)", "application", "BuildConfig"));
  }

  public void testGeneratedResources() throws Exception {
    File projectRoot = prepareProjectForImport(TestProjectPaths.SIMPLE_APPLICATION);
    Files.append(
      "android {\n" +
      "  String resGeneratePath = \"${buildDir}/generated/my_generated_resources/res\"\n" +
      "    def generateResTask = tasks.create(name: 'generateMyResources').doLast {\n" +
      "        def rawDir = \"${resGeneratePath}/raw\"\n" +
      "        mkdir(rawDir)\n" +
      "        file(\"${rawDir}/sample_raw_resource\").write(\"sample text\")\n" +
      "    }\n" +
      "\n" +
      "    def resDir = files(resGeneratePath).builtBy(generateResTask)\n" +
      "\n" +
      "    applicationVariants.all { variant ->\n" +
      "        variant.registerGeneratedResFolders(resDir)\n" +
      "    }\n" +
      "}",
      new File(projectRoot, "app/build.gradle"),
      StandardCharsets.UTF_8);
    requestSyncAndWait();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    GradleAndroidModel androidModel = GradleAndroidModel.get(appModule);
    File generatedResourcesFolder = androidModel.getMainArtifact()
      .getGeneratedResourceFolders()
      .stream()
      .filter(f -> f.getPath().contains("my_generated_resources"))
      .findFirst()
      .orElse(null);
    assertThat(generatedResourcesFolder).named("my_generated_resources folder").isNotNull();
    File resourceFile = FileUtils.join(generatedResourcesFolder, "raw", "sample_raw_resource");
    Files.createParentDirs(resourceFile);
    Files.write("sample text", resourceFile, StandardCharsets.UTF_8);

    refreshProjectFiles();

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Set<List<String>> allNodes = getAllNodes(structure);
    assertThat(allNodes).contains(Arrays.asList("app (Android)", "res (generated)", "raw", "sample_raw_resource"));
  }

  public void testShowVisibilityIconsWhenOptionIsSelected() {
    ProjectViewState projectViewState = getProject().getService(ProjectViewState.class);
    projectViewState.setShowVisibilityIcons(true);

    myPane = createPane();
    ProjectAbstractTreeStructureBase structure = myPane.createStructure();
    assertTrue(((ProjectViewSettings)structure).isShowVisibilityIcons());
  }

  public void testShowVisibilityIconsWhenOptionIsUnselected() {
    ProjectViewState projectViewState = getProject().getService(ProjectViewState.class);
    projectViewState.setShowVisibilityIcons(false);

    myPane = createPane();
    ProjectAbstractTreeStructureBase structure = myPane.createStructure();
    assertFalse(((ProjectViewSettings)structure).isShowVisibilityIcons());
  }

  public void testResourcesPropertiesInAndroidView() throws Exception {
    loadSimpleApplication();
    FileUtils.createFile(new File(getProjectFolderPath() + "/app/src/main/res/resources.properties"), "");

    refreshProjectFiles();
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(getProject());
    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Set<List<String>> allNodes = getAllNodes(structure);
    assertThat(allNodes).contains(Arrays.asList("app (Android)", "res", "resources.properties (main)"));
  }

  private static Set<List<String>> getAllNodes(TestAndroidTreeStructure structure) {
    Set<List<String>> result = new HashSet<>();
    Stack<String> path = new Stack<>();
    Object root = structure.getRootElement();
    getAllNodes(structure, root, path, result);
    return result;
  }

  private static void getAllNodes(TestAndroidTreeStructure structure,
                                  Object node,
                                  Stack<String> path,
                                  Set<List<String>> result) {
    for (Object child : structure.getChildElements(node)) {
      String nodeName = ((AbstractTreeNode)child).toTestString(null);
      if (structure.getChildElements(child).length == 0) {
        ArrayList<String> newPath = new ArrayList<>(path);
        newPath.add(nodeName);
        result.add(newPath);
      } else {
        path.push(nodeName);
        getAllNodes(structure, child, path, result);
        path.pop();
      }
    }
  }

  private class TestAndroidTreeStructure extends TestProjectTreeStructure {
    private TestAndroidTreeStructure(Project project, Disposable parentDisposable) {
      super(project, parentDisposable);
    }

    @Override
    public List<TreeStructureProvider> getProviders() {
      List<TreeStructureProvider> providers = super.getProviders();
      if (providers == null) {
        return null;
      }
      return ContainerUtil.map(providers, provider -> new BuildScriptTreeStructureProvider(provider));
    }

    @Override
    protected AbstractTreeNode createRoot(@NotNull Project project, @NotNull ViewSettings settings) {
      return new AndroidViewProjectNode(project, settings);
    }

    @Override
    public boolean isHideEmptyMiddlePackages() {
      return true;
    }
  }

  @NotNull
  private AndroidProjectViewPane createPane() {
    AndroidProjectViewPane pane = new AndroidProjectViewPane(getProject());
    pane.createComponent();
    Disposer.register(getProject(), pane);
    return pane;
  }
}
