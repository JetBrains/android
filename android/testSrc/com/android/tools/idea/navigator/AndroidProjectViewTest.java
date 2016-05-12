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

import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

// TODO: Test available actions for each node!
public class AndroidProjectViewTest extends AndroidGradleTestCase {
  private AndroidProjectViewPane myPane;

  public void testProjectView() throws Exception {
    loadProject("projects/navigator/packageview/simple");

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), myTestRootDisposable);

    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    PsiDirectory dir = getBaseFolder();
    assertNotNull(dir);

    String rootModuleName = null;
    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      if (GradleUtil.getGradlePath(module) == null) {
        rootModuleName = module.getName();
      }
    }
    assertNotNull(rootModuleName);

    String projectName = getProject().getName();
    String expected =
      projectName + "\n" +
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
      "    ic_launcher.png (2)\n" +
      "     ic_launcher.png (hdpi, debug)\n" +
      "     ic_launcher.png (mdpi)\n" +
      "    j.png (mdpi)\n" +
      "   layout\n" +
      "    activity_main.xml\n" +
      "   menu\n" +
      "    main.xml\n" +
      "   values\n" +
      "    dimens.xml (3)\n" +
      "     dimens.xml\n" +
      "     dimens.xml (debug)\n" +
      "     dimens.xml (w820dp)\n" +
      "    strings.xml (2)\n" +
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
      "  build.gradle (Project: " + rootModuleName + ")\n" +
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
    ProjectViewTestUtil
      .assertStructureEqual(structure, expected, numLines, new GroupByTypeComparator(null, "android"), structure.getRootElement(),
                            printInfo);
  }

  // Test that selecting a res group node causes the correct PSI Elements to be selected
  public void testSelection() throws Exception {
    loadProject("projects/navigator/packageview/simple");

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), myTestRootDisposable);

    // Select the node app/res/values/dimens.xml, which groups together 3 dimens.xml files
    Object element = findElementForPath(structure, "app (Android)", "res", "values", "dimens.xml (3)");
    assertNotNull(element);
    myPane.getTreeBuilder().select(element);

    // Now make sure that selecting that group node caused the actual files to be selected
    PsiElement[] psiElements = myPane.getSelectedPSIElements();
    assertEquals(3, psiElements.length);
    for (PsiElement e : psiElements) {
      assertEquals("dimens.xml", ((XmlFile)e).getName());
    }
  }

  // Test that the virtualFileArray for resource nodes actually contains the files for this node.
   public void testVirtualFileArrayForResNode() throws Exception {
    loadProject("projects/navigator/packageview/simple");

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), myTestRootDisposable);

    // Select the node app/res/drawable/is_launcher.png, which groups together 2 ic_launcher.png files.
    Object element = findElementForPath(structure, "app (Android)", "res", "drawable", "ic_launcher.png (2)");
    assertNotNull(element);
    myPane.getTreeBuilder().select(element);

    // Now make sure the virtualFileArray for this node actually contains the 2 files.
    VirtualFile[] files = ((VirtualFile[])myPane.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName()));
    assertEquals(2, files.length);
    for (VirtualFile f : files) {
      assertEquals("ic_launcher.png", f.getName());
    }
  }

  @Nullable
  private Object findElementForPath(TestAndroidTreeStructure structure, String... path) {
    Object current = structure.getRootElement();

    outer: for (String segment : path) {
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
    loadProject("projects/navigator/packageview/commonroots");

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), myTestRootDisposable);

    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    PsiDirectory dir = getBaseFolder();
    assertNotNull(dir);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertEquals(1, modules.length);

    String projectName = getProject().getName();
    String expected =
      projectName + "\n" +
      " Gradle Scripts\n" +
      "  build.gradle (Module: " + modules[0].getName() + ")\n" +
      "  gradle-wrapper.properties (Gradle Version)\n" +
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
    ProjectViewTestUtil
      .assertStructureEqual(structure, expected, numLines, PlatformTestUtil.createComparator(printInfo), structure.getRootElement(),
                            printInfo);
  }

  public void testFailedImport() throws Exception {
    loadProject("projects/navigator/invalid", false, new GradleSyncListener.Adapter() {
      @Override
      public void syncFailed(@NotNull final Project project, @NotNull String errorMessage) {
        // If the sync fails, then IDE creates an empty top level module. Mimic the same behavior for this test.
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                NewProjectImportGradleSyncListener.createTopLevelModule(project);
              }
            });
          }
        });
      }
    });

    myPane = createPane();
    TestAndroidTreeStructure structure = new TestAndroidTreeStructure(getProject(), myTestRootDisposable);

    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    PsiDirectory dir = getBaseFolder();
    assertNotNull(dir);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertEquals(1, modules.length);

    String projectName = getProject().getName();
    String expected =
      projectName + "\n" +
      " Gradle Scripts\n" +
      "  build.gradle (Project: " + modules[0].getName() + ")\n" +
      "  gradle-wrapper.properties (Gradle Version)\n" +
      " " + modules[0].getName() + "\n" +
      "  .idea\n" +
      "  AndroidManifest.xml\n" +
      "  build.gradle\n" +
      "  gradle\n" +
      "   wrapper\n" +
      "    gradle-wrapper.jar\n" +
      "    gradle-wrapper.properties\n" +
      "  gradlew\n" +
      "  gradlew.bat\n";
    int numLines = expected.split("\n").length;

    Object rootNode = structure.getRootElement();
    ProjectViewTestUtil.checkGetParentConsistency(structure, rootNode);
    Comparator<AbstractTreeNode> comparator = PlatformTestUtil.createComparator(printInfo);

    // Android Studio now bundles a local maven repo. Our gradle builds pass this via an init script. It turns out that this drops
    // in an additional gradle file into the project view. This gradle file is named asLocalRepo???.gradle. Since we can't predict
    // the exact name, we just trim it out from the actual output.
    String actual = PlatformTestUtil.print(structure, rootNode, 0, comparator, numLines + 1, ' ', printInfo).toString();
    List<String> filtered = Lists.newArrayList();
    for (String s : Splitter.on('\n').split(actual)) {
      if (!s.contains("asLocalRepo")) {
        filtered.add(s);
      }
    }

    actual = Joiner.on('\n').join(filtered);

    assertEquals(expected, actual);
  }

  @Nullable
  private PsiDirectory getBaseFolder() throws Exception {
    VirtualFile folder = getProject().getBaseDir();
    assertNotNull("project basedir is null!", folder);
    return PsiManager.getInstance(getProject()).findDirectory(folder);
  }

  private class TestAndroidTreeStructure extends TestProjectTreeStructure {
    public TestAndroidTreeStructure(Project project, Disposable parentDisposable) {
      super(project, parentDisposable);
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

  private AndroidProjectViewPane createPane() {
    final AndroidProjectViewPane pane = new AndroidProjectViewPane(getProject());
    pane.createComponent();
    Disposer.register(getProject(), pane);
    return pane;
  }
}
