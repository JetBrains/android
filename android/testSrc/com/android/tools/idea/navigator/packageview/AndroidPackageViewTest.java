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
package com.android.tools.idea.navigator.packageview;

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class AndroidPackageViewTest extends AndroidGradleTestCase {
  public void test1() throws Exception {
    loadProject("projects/navigator/packageview/simple");

    TestPackageTreeStructure structure = new TestPackageTreeStructure(getProject(), myTestRootDisposable);
    structure.createPane();
    structure.setProviders(new TestAndroidPackageViewProvider());

    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    PsiDirectory dir = getBaseFolder();
    assertNotNull(dir);

    String expected =
      "Project\n" +
      " app (Android)\n" +
      "  java\n" +
      "   app\n" +
      "    MainActivity.java\n" +
      "  manifests\n" +
      "   AndroidManifest.xml [debug]\n" +
      "   AndroidManifest.xml [main]\n" +
      "  res\n" +
      "   drawable-mdpi\n" +
      "    ic_launcher.png\n" +
      "   layout\n" +
      "    activity_main.xml\n" +
      "   menu\n" +
      "    main.xml\n" +
      "   values\n" +
      "    dimens.xml\n" +
      "    strings.xml\n" +
      "    styles.xml\n" +
      "   values-w820dp\n" +
      "    dimens.xml\n" +
      " lib (Android)\n" +
      "  java\n" +
      "   lib\n" +
      "  manifests\n" +
      "   AndroidManifest.xml [main]\n" +
      "  res\n" +
      "   drawable-mdpi\n" +
      "    ic_launcher.png\n" +
      "   values\n" +
      "    strings.xml\n";
    int numLines = expected.split("\n").length;
    ProjectViewTestUtil.assertStructureEqual(structure, expected, numLines, PlatformTestUtil.createComparator(printInfo),
                                             structure.getRootElement(), printInfo);
  }

  @Nullable
  private PsiDirectory getBaseFolder() throws Exception {
    VirtualFile folder = getProject().getBaseDir();
    assertNotNull("project basedir is null!", folder);
    return PsiManager.getInstance(getProject()).findDirectory(folder);
  }

  private static class TestAndroidPackageViewProvider extends AndroidPackageViewProvider {
    @Override
    protected boolean isProviderEnabled(AbstractTreeNode parent, @Nullable Project project) {
      return true;
    }
  }

  private static class TestPackageTreeStructure extends TestProjectTreeStructure {
    public TestPackageTreeStructure(Project project, Disposable parentDisposable) {
      super(project, parentDisposable);
    }

    @Override
    protected AbstractTreeNode createRoot(Project project, ViewSettings settings) {
      return new PackageViewProjectNode(project, settings);
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
}
