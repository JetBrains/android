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

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL;
import static com.android.tools.idea.testing.TestProjectPaths.NAVIGATOR_PACKAGEVIEW_COMMONROOTS;
import static com.android.tools.idea.testing.TestProjectPaths.NAVIGATOR_PACKAGEVIEW_SIMPLE;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.testFramework.PlatformTestUtil.createComparator;
import static com.intellij.testFramework.ProjectViewTestUtil.assertStructureEqual;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode;
import com.android.tools.idea.navigator.nodes.android.BuildScriptTreeStructureProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.tools.idea.testing.TestProjectPaths;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
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
import org.jetbrains.annotations.Nullable;

// TODO: Test available actions for each node!
public class AndroidProjectViewTest extends AndroidGradleTestCase {
  private AndroidProjectViewPane myPane;

  public void testProjectView() throws Exception {
    loadProject(NAVIGATOR_PACKAGEVIEW_SIMPLE);

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    PsiDirectory dir = getBaseFolder();
    assertNotNull(dir);

    String rootModuleName = null;
    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      if (getGradlePath(module) == null) {
        rootModuleName = module.getName();
      }
    }
    assertNotNull(rootModuleName);

    String projectName = getProject().getName();
    String expected = projectName + "\n" +
                      " app (Android)\n" +
                      "  manifests\n" +
                      "   AndroidManifest.xml (main)\n" +
                      "   AndroidManifest.xml (debug)\n" +
                      "  java\n" +
                      "   app (main)\n" +
                      "    MainActivity\n" +
                      "   app (androidTest)\n" +
                      "    MainActivityTest.java\n" +
                      "   Debug.java\n" +
                      "  renderscript\n" +
                      "   test.rs (main)\n" +
                      "  assets\n" +
                      "   raw.asset.txt (main)\n" +
                      "  res\n" +
                      "   drawable\n" +
                      "    ic_launcher (2)\n" +
                      "     ic_launcher.png (hdpi, debug)\n" +
                      "     ic_launcher.png (mdpi)\n" +
                      "    j (2)\n" +
                      "     j.png (mdpi)\n" +
                      "     j.xml (xxdpi)\n" +
                      "   layout\n" +
                      "    activity_main.xml\n" +
                      "   menu\n" +
                      "    main.xml\n" +
                      "   values\n" +
                      "    dimens (3)\n" +
                      "     dimens.xml\n" +
                      "     dimens.xml (debug)\n" +
                      "     dimens.xml (w820dp)\n" +
                      "    strings (2)\n" +
                      "     strings.xml\n" +
                      "     strings.xml (debug)\n" +
                      "    styles.xml\n" +
                      " empty (non-Android)\n" +
                      " javamodule (non-Android)\n" +
                      "  java\n" +
                      "   foo\n" +
                      "    Foo.java\n" +
                      "  tests\n" +
                      "   foo\n" +
                      "    FooTest.java\n" +
                      "  resources\n" +
                      "   res2.txt\n" +
                      "  test-resources\n" +
                      "   test-res.txt\n" +
                      " lib (Android)\n" +
                      "  manifests\n" +
                      "   AndroidManifest.xml (main)\n" +
                      "  jniLibs\n" +
                      "   libc.so (main)\n" +
                      "   libm.so (debug)\n" +
                      "  res\n" +
                      "   drawable\n" +
                      "    ic_launcher.png (mdpi)\n" +
                      "   values\n" +
                      "    strings.xml\n" +
                      " Gradle Scripts\n" +
                      "  build.gradle (Project: testProjectView)\n" +
                      "  build.gradle (Module: testProjectView.app)\n" +
                      "  sonar.gradle (Module: testProjectView.app)\n" +
                      "  build.gradle (Module: testProjectView.empty)\n" +
                      "  build.gradle (Module: testProjectView.javamodule)\n" +
                      "  build.gradle (Module: testProjectView.lib)\n" +
                      "  gradle-wrapper.properties (Gradle Version)\n" +
                      "  proguard-rules.pro (ProGuard Rules for testProjectView.app)\n" +
                      "  proguard.cfg (ProGuard Rules for testProjectView.lib)\n" +
                      "  gradle.properties (Project Properties)\n" +
                      "  settings.gradle (Project Settings)\n" +
                      "  local.properties (SDK Location)\n";
    int numLines = expected.split("\n").length;
    assertStructureEqual(structure, expected, numLines, new GroupByTypeComparator(null, "android"), structure.getRootElement(), printInfo);
  }

  // Test that selecting a res group node causes the correct PSI Elements to be selected
  // TODO(b/156367441): Re-implement
  public void /*test*/Selection() throws Exception {
    loadProject(NAVIGATOR_PACKAGEVIEW_SIMPLE);

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    // Select the node app/res/values/dimens.xml, which groups together 3 dimens.xml files
    Object element = findElementForPath(structure, "app (Android)", "res", "values", "dimens (3)");
    assertNotNull(element);
    myPane.getTreeBuilder().select(element);

    // Now make sure that selecting that group node caused the actual files to be selected
    PsiElement[] psiElements = myPane.getSelectedPSIElements();
    assertThat(psiElements).hasLength(3);
    for (PsiElement e : psiElements) {
      assertEquals("dimens.xml", ((XmlFile)e).getName());
    }
  }

  // Test that the virtualFileArray for resource nodes actually contains the files for this node.
  // TODO(b/156367441): Re-implement
  public void /*test*/VirtualFileArrayForResNode() throws Exception {
    loadProject(NAVIGATOR_PACKAGEVIEW_SIMPLE);

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    // Select the node app/res/drawable/is_launcher.png, which groups together 2 ic_launcher.png files.
    Object element = findElementForPath(structure, "app (Android)", "res", "drawable", "ic_launcher (2)");
    assertNotNull(element);
    myPane.getTreeBuilder().select(element);

    // Now make sure the virtualFileArray for this node actually contains the 2 files.
    VirtualFile[] files = ((VirtualFile[])myPane.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName()));
    assertNotNull(files);
    assertThat(files).hasLength(2);
    for (VirtualFile f : files) {
      assertEquals("ic_launcher.png", f.getName());
    }
  }

  @Nullable
  private Object findElementForPath(@NotNull TestAndroidTreeStructure structure, @NotNull String... path) {
    Object current = structure.getRootElement();

    outer:
    for (String segment : path) {
      for (Object child : structure.getChildElements(current)) {
        AbstractTreeNode node = (AbstractTreeNode)child;
        if (segment.equals(node.toTestString(null))) {
          current = node;
          continue outer;
        }
      }

      // none of the children match the expected segment
      return null;
    }
    return current;
  }

  public void testCommonRoots() throws Exception {
    loadProject(NAVIGATOR_PACKAGEVIEW_COMMONROOTS);

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    PsiDirectory dir = getBaseFolder();
    assertNotNull(dir);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertThat(modules).hasLength(1);

    String projectName = getProject().getName();
    String expected = projectName + "\n" +
                      " Gradle Scripts\n" +
                      "  build.gradle (Module: " + modules[0].getName() + ")\n" +
                      "  gradle-wrapper.properties (Gradle Version)\n" +
                      "  gradle.properties (Project Properties)\n" +
                      "  local.properties (SDK Location)\n" +
                      " " + modules[0].getName() + " (Android)\n" +
                      "  java\n" +
                      "   foo (main)\n" +
                      "    Foo.java\n" +
                      "  manifests\n" +
                      "   AndroidManifest.xml (main)\n" +
                      "  res\n" +
                      "   values\n" +
                      "    dimens.xml (w820dp)\n" +
                      "  resources\n" +
                      "   sample_resource.txt\n";
    int numLines = expected.split("\n").length;
    assertStructureEqual(structure, expected, numLines, createComparator(printInfo), structure.getRootElement(), printInfo);
  }

  public void testKotlinBuildScriptStructure() throws Exception {
    loadProject(KOTLIN_GRADLE_DSL);

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    PsiDirectory dir = getBaseFolder();
    assertNotNull(dir);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertThat(modules).hasLength(3);

    String projectName = getProject().getName();
    String expected = projectName + "\n" +
                      " Gradle Scripts\n" +
                      "  build.gradle (Project: " + projectName +  ")\n" +
                      "  build.gradle.kts (Module: testKotlinBuildScriptStructure.app)\n" +
                      "  build.gradle.kts (Module: testKotlinBuildScriptStructure.lib)\n" +
                      "  build.gradle.kts (Project: " + projectName + ")\n" +
                      "  gradle-wrapper.properties (Gradle Version)\n" +
                      "  gradle.properties (Project Properties)\n" +
                      "  local.properties (SDK Location)\n" +
                      "  settings.gradle.kts (Project Settings)\n" +
                      " app (Android)\n" +
                      "  kotlin\n" +
                      "   kotlingradle (main)\n" +
                      "    CLASS\n" +
                      "  manifests\n" +
                      "   AndroidManifest.xml (main)\n" +
                      "  res\n" +
                      "   layout\n" +
                      "    activity_main.xml\n" +
                      "   mipmap\n" +
                      "    ic_launcher (5)\n" +
                      "     ic_launcher.png (hdpi)\n" +
                      "     ic_launcher.png (mdpi)\n" +
                      "     ic_launcher.png (xhdpi)\n" +
                      "     ic_launcher.png (xxhdpi)\n" +
                      "     ic_launcher.png (xxxhdpi)\n" +
                      "   values\n" +
                      "    colors.xml\n" +
                      "    dimens (2)\n" +
                      "     dimens.xml\n" +
                      "     dimens.xml (w820dp)\n" +
                      "    strings.xml\n" +
                      "    styles.xml\n" +
                      " lib (Android)\n" +
                      "  kotlin\n" +
                      "   lib (main)\n" +
                      "    LibMain.kt\n" +
                      "  manifests\n" +
                      "   AndroidManifest.xml (main)\n";
    int numLines = expected.split("\n").length;
    assertStructureEqual(structure, expected, numLines, createComparator(printInfo), structure.getRootElement(), printInfo);
  }

  // TODO(b/156367441): Re-implement
  public void /*test*/ResourceGroupWithDifferentExtension() throws Exception {
    loadProject(NAVIGATOR_PACKAGEVIEW_SIMPLE);

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

    // Select the node app/res/drawable/is_launcher.png, which groups together 2 ic_launcher.png files.
    Object element = findElementForPath(structure, "app (Android)", "res", "drawable", "j (2)");
    assertNotNull(element);
    myPane.getTreeBuilder().select(element);

    // Now make sure the virtualFileArray for this node actually contains the 2 files.
    VirtualFile[] files = ((VirtualFile[])myPane.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName()));
    assertNotNull(files);
    assertThat(files).hasLength(2);
    List<String> fileNames = Arrays.stream(files).map(VirtualFile::getName).collect(Collectors.toList());
    assertSameElements(fileNames, ImmutableList.of("j.png", "j.xml"));
  }

  public void testGeneratedSourceFiles_lightClasses() throws Exception {
    loadSimpleApplication();

    // Create BuildConfig.java in one of the generated source folders.
    Module appModule = TestModuleUtil.findAppModule(getProject());
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
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
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
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

  @Nullable
  private PsiDirectory getBaseFolder() {
    VirtualFile folder = ProjectUtil.guessProjectDir(getProject());
    assertNotNull("project basedir is null!", folder);
    return PsiManager.getInstance(getProject()).findDirectory(folder);
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
      return new AndroidViewProjectNode(project, settings, myPane);
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
