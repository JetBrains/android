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
package com.android.tools.idea.navigator;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode;
import com.android.tools.idea.navigator.nodes.android.BuildScriptTreeStructureProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.testFramework.PlatformTestUtil.createComparator;
import static com.intellij.testFramework.ProjectViewTestUtil.assertStructureEqual;

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
                      "  cpp\n" +
                      "   hello.c (main)\n" +
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
                      "  build.gradle (Module: app)\n" +
                      "  sonar.gradle (Module: app)\n" +
                      "  build.gradle (Module: empty)\n" +
                      "  build.gradle (Module: javamodule)\n" +
                      "  build.gradle (Module: lib)\n" +
                      "  gradle-wrapper.properties (Gradle Version)\n" +
                      "  proguard-rules.pro (ProGuard Rules for app)\n" +
                      "  proguard.cfg (ProGuard Rules for lib)\n" +
                      "  settings.gradle (Project Settings)\n" +
                      "  local.properties (SDK Location)\n";
    int numLines = expected.split("\n").length;
    assertStructureEqual(structure, expected, numLines, new GroupByTypeComparator(null, "android"), structure.getRootElement(), printInfo);
  }

  // Test that selecting a res group node causes the correct PSI Elements to be selected
  public void testSelection() throws Exception {
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
  public void testVirtualFileArrayForResNode() throws Exception {
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
                      "  build.gradle.kts (Module: app)\n" +
                      "  build.gradle.kts (Module: lib)\n" +
                      "  build.gradle.kts (Project: " + projectName + ")\n" +
                      "  gradle-wrapper.properties (Gradle Version)\n" +
                      "  local.properties (SDK Location)\n" +
                      "  settings.gradle.kts (Project Settings)\n" +
                      " app (Android)\n" +
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
                      "  manifests\n" +
                      "   AndroidManifest.xml (main)\n";
    int numLines = expected.split("\n").length;
    assertStructureEqual(structure, expected, numLines, createComparator(printInfo), structure.getRootElement(), printInfo);
  }

  public void testResourceGroupWithDifferentExtension() throws Exception {
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

  public void testGeneratedSourceFiles_aaptClasses() throws Exception {
    doTestGeneratedSourceFiles(false, false);
  }

  public void testGeneratedSourceFiles_lightClasses_oldModel() throws Exception {
    doTestGeneratedSourceFiles(false, true);
  }

  public void testGeneratedSourceFiles_lightClasses_newModel() throws Exception {
    doTestGeneratedSourceFiles(true, true);
  }

  // Test that the generated source files are displayed under app/generatedJava.
  private void doTestGeneratedSourceFiles(boolean preSyncOverride, boolean postSyncOverride) throws Exception {
    try {
      // Setting the IDE flag results in a new property being passed to Gradle, which will cause the folder to not be in the model. But the
      // property is only recognized by new versions of AGP, so we need to also make sure that if the directories are in the model we ignore
      // them in the UI.
      StudioFlags.IN_MEMORY_R_CLASSES.override(preSyncOverride);
      loadSimpleApplication();
      StudioFlags.IN_MEMORY_R_CLASSES.override(postSyncOverride);

      // Create BuildConfig.java in one of the generated source folders.
      Module appModule = myModules.getAppModule();
      AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
      assertNotNull(androidModel);
      Collection<File> generatedFolders = androidModel.getMainArtifact().getGeneratedSourceFolders();
      assertThat(generatedFolders).isNotEmpty();

      File buildConfigFolder = generatedFolders.stream().filter(f -> f.getPath().contains("buildConfig")).findFirst().orElse(null);
      assertNotNull(buildConfigFolder);
      writeToFile(new File(buildConfigFolder, join("com", "application", "BuildConfig.java")),
                  "package com.application; public final class BuildConfig {}");

      if (!preSyncOverride) {
        File rClassesFolder = generatedFolders.stream().filter(f -> f.getName().equals("r")).findFirst().orElse(null);
        assertNotNull(rClassesFolder);
        writeToFile(new File(rClassesFolder, join("com", "application", "R.java")),
                    "package com.application; public final class R {}");
      }
      LocalFileSystem.getInstance().refresh(false/* synchronously */);

      myPane = createPane();
      TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), getTestRootDisposable());

      Set<List<String>> allNodes = getAllNodes(structure);
      assertTrue(allNodes.contains(Arrays.asList("app (Android)", "generatedJava", "application", "BuildConfig")));

      // The R class should only be displayed if we're using R.java from aapt, not light PSI.
      assertEquals(!postSyncOverride, allNodes.contains(Arrays.asList("app (Android)", "generatedJava", "application", "R")));
    }
    finally {
      StudioFlags.IN_MEMORY_R_CLASSES.clearOverride();
    }
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
    VirtualFile folder = getProject().getBaseDir();
    assertNotNull("project basedir is null!", folder);
    return PsiManager.getInstance(getProject()).findDirectory(folder);
  }

  private class TestAndroidTreeStructure extends TestProjectTreeStructure {
    public TestAndroidTreeStructure(Project project, Disposable parentDisposable) {
      super(project, parentDisposable);
    }

    @Override
    public List<TreeStructureProvider> getProviders() {
      List<TreeStructureProvider> providers = super.getProviders();
      if (providers == null) {
        return null;
      }
      return providers.stream().map(provider ->  new BuildScriptTreeStructureProvider(provider)).collect(Collectors.toList());
    }

    @Override
    protected AbstractTreeNode createRoot(Project project, ViewSettings settings) {
      return new AndroidViewProjectNode(project, settings, myPane);
    }

    @Override
    public boolean isShowLibraryContents() {
      return false;
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
