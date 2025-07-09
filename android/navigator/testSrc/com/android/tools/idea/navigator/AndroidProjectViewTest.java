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
import static org.mockito.Mockito.when;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.projectView.AndroidProjectViewSettingsImpl;
import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode;
import com.android.tools.idea.navigator.nodes.android.BuildScriptTreeStructureProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.tools.idea.testing.TestProjectPaths;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.ProjectViewDefaultViewEvent;
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
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

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
      """

        android {
          String resGeneratePath = "${buildDir}/generated/my_generated_resources/res"
            def generateResTask = tasks.create(name: 'generateMyResources').doLast {
                def rawDir = "${resGeneratePath}/raw"
                mkdir(rawDir)
                file("${rawDir}/sample_raw_resource").write("sample text")
            }

            def resDir = files(resGeneratePath).builtBy(generateResTask)

            applicationVariants.all { variant ->
                variant.registerGeneratedResFolders(resDir)
            }
        }""",
      new File(projectRoot, "app/build.gradle"),
      StandardCharsets.UTF_8);
    importProject();

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
    Files.write("\nsample text", resourceFile, StandardCharsets.UTF_8);

    refreshProjectFiles();

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Set<List<String>> allNodes = getAllNodes(structure);
    assertThat(allNodes).contains(Arrays.asList("app (Android)", "res (generated)", "raw", "sample_raw_resource"));
  }

  public void testGeneratedAssets() throws Exception {
    File projectRoot = prepareProjectForImport(TestProjectPaths.SIMPLE_APPLICATION);
    Files.append(
      """
       
       abstract class AssetGenerator extends DefaultTask {
           @OutputDirectory
           abstract DirectoryProperty getOutputDirectory();
           @TaskAction
           void run() {
               def outputFile = new File(getOutputDirectory().get().getAsFile(), "foo.txt")
               new FileWriter(outputFile).with {
                   write("some text")
                   flush()
               }
           }
       }

       def writeAssetTask = tasks.register("createAssets", AssetGenerator.class)
       androidComponents {
           onVariants(selector().all(),  { variant ->
               variant.sources.assets.addGeneratedSourceDirectory(writeAssetTask, AssetGenerator::getOutputDirectory)
           })
       }""",
      new File(projectRoot, "app/build.gradle"),
      StandardCharsets.UTF_8);
    importProject();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    GradleAndroidModel androidModel = GradleAndroidModel.get(appModule);
    File generatedAssetsFolder = androidModel.getMainArtifact()
      .getGeneratedAssetFolders()
      .stream()
      .filter(f -> f.getPath().contains("createAssets"))
      .findFirst()
      .orElse(null);
    assertThat(generatedAssetsFolder).named("createAssets folder").isNotNull();

    File assetFile = FileUtils.join(generatedAssetsFolder, "raw", "createAssets");
    Files.createParentDirs(assetFile);
    Files.write("\nsample text", assetFile, StandardCharsets.UTF_8);

    refreshProjectFiles();

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Set<List<String>> allNodes = getAllNodes(structure);
    assertThat(allNodes).contains(Arrays.asList("app (Android)", "assets (generated)", "raw", "createAssets"));
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

  public void testAndroidViewIsDefault() {
    myPane = createPane();
    IdeInfo ideInfo = Mockito.spy(IdeInfo.getInstance());
    AndroidProjectViewSettingsImpl settings = new AndroidProjectViewSettingsImpl();
    Project project = getProject();

    when(ideInfo.isAndroidStudio()).thenReturn(false);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault").isFalse();

    when(ideInfo.isAndroidStudio()).thenReturn(true);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(AndroidStudio)").isTrue();

    when(ideInfo.isAndroidStudio()).thenReturn(false);
    when(ideInfo.isGameTools()).thenReturn(true);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(GameTools)").isTrue();

    System.setProperty("studio.projectview", "true");
    assertThat(settings.isDefaultToProjectViewEnabled()).isFalse();
    when(ideInfo.isAndroidStudio()).thenReturn(false);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(property)").isFalse();

    when(ideInfo.isAndroidStudio()).thenReturn(true);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(AndroidStudio, property)").isFalse();

    when(ideInfo.isAndroidStudio()).thenReturn(false);
    when(ideInfo.isGameTools()).thenReturn(true);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(GameTools, property)").isFalse();

    settings.setDefaultToProjectView(true);
    System.setProperty("studio.projectview", "false");
    assertThat(settings.isDefaultToProjectViewEnabled()).isTrue();
    when(ideInfo.isAndroidStudio()).thenReturn(false);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(settings)").isFalse();

    when(ideInfo.isAndroidStudio()).thenReturn(true);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(AndroidStudio, settings)").isFalse();

    when(ideInfo.isAndroidStudio()).thenReturn(false);
    when(ideInfo.isGameTools()).thenReturn(true);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(GameTools, settings)").isFalse();
  }

  public void testAndroidViewIsDefaultMetrics() {
    myPane = createPane();
    IdeInfo ideInfo = Mockito.spy(IdeInfo.getInstance());
    AndroidProjectViewSettingsImpl settings = new AndroidProjectViewSettingsImpl();
    Project project = getProject();

    System.setProperty("studio.projectview", "false");
    settings.setDefaultToProjectView(true);

    TestUsageTracker testUsageTracker = new TestUsageTracker(new VirtualTimeScheduler());
    UsageTracker.setWriterForTest(testUsageTracker);

    settings.setDefaultToProjectView(false);
    assertThat(settings.isDefaultToProjectViewEnabled()).isTrue();
    when(ideInfo.isAndroidStudio()).thenReturn(true);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(AndroidStudio)").isTrue();

    settings.setDefaultToProjectView(true);
    assertThat(settings.isDefaultToProjectViewEnabled()).isTrue();
    when(ideInfo.isAndroidStudio()).thenReturn(true);
    when(ideInfo.isGameTools()).thenReturn(false);
    assertThat(myPane.isDefaultPane(project, ideInfo, settings)).named("isDefault(AndroidStudio)").isFalse();

    // Assert that event is not logged when setting is set to current value
    settings.setDefaultToProjectView(true);

    List<AndroidStudioEvent> statsEvents = testUsageTracker.getUsages().stream()
      .map(usage -> usage.getStudioEvent())
      .filter(event -> event.getKind() == AndroidStudioEvent.EventKind.PROJECT_VIEW_DEFAULT_VIEW_EVENT)
      .collect(Collectors.toList());
    assertThat(statsEvents.size()).isEqualTo(2);

    AndroidStudioEvent disableDefaultProjectViewEvent = statsEvents.get(0);
    assertThat(disableDefaultProjectViewEvent.getKind()).isEqualTo(AndroidStudioEvent.EventKind.PROJECT_VIEW_DEFAULT_VIEW_EVENT);
    assertThat(disableDefaultProjectViewEvent.getProjectViewDefaultViewEvent().getDefaultView()).isEqualTo(
      ProjectViewDefaultViewEvent.DefaultView.ANDROID_VIEW);

    AndroidStudioEvent enableDefaultProjectViewEvent = statsEvents.get(1);
    assertThat(enableDefaultProjectViewEvent.getKind()).isEqualTo(AndroidStudioEvent.EventKind.PROJECT_VIEW_DEFAULT_VIEW_EVENT);
    assertThat(enableDefaultProjectViewEvent.getProjectViewDefaultViewEvent().getDefaultView()).isEqualTo(
      ProjectViewDefaultViewEvent.DefaultView.PROJECT_VIEW);

    UsageTracker.cleanAfterTesting();
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