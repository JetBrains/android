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
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      "  assets\n" +
      "   raw.asset.txt\n" +
      "  rs\n" +
      "   test.rs\n" +
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
      "  res\n" +
      "   drawable\n" +
      "    ic_launcher.png (mdpi)\n" +
      "   values\n" +
      "    strings.xml\n" +
      "  c\n" +
      "   hello.c\n" +
      "  jniLibs\n" +
      "   libc.so\n" +
      " Gradle Scripts\n" +
      "  build.gradle (app)\n" +
      "  sonar.gradle (app)\n" +
      "  build.gradle (empty)\n" +
      "  build.gradle (javamodule)\n" +
      "  build.gradle (lib)\n" +
      "  build.gradle (" + rootModuleName + ")\n" +
      "  settings.gradle (Project Settings)\n" +
      "  gradle-wrapper.properties\n" +
      "  local.properties\n";
    int numLines = expected.split("\n").length;
    ProjectViewTestUtil
      .assertStructureEqual(structure, expected, numLines, new GroupByTypeComparator(null, "android"), structure.getRootElement(),
                            printInfo);
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
      "  build.gradle (" + modules[0].getName() + ")\n" +
      "  gradle-wrapper.properties\n" +
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
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            NewProjectImportGradleSyncListener.createTopLevelModule(project);
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
      "  build.gradle (" + modules[0].getName() + ")\n" +
      "  gradle-wrapper.properties\n" +
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
    ProjectViewTestUtil
      .assertStructureEqual(structure, expected, numLines, PlatformTestUtil.createComparator(printInfo), structure.getRootElement(),
                            printInfo);
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
