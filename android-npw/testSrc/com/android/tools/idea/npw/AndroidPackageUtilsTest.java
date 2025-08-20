/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mockito;

import java.io.File;

import static com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidPackageUtils}.
 */
@RunsInEdt
public final class AndroidPackageUtilsTest {
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule(
    "tools/adt/idea/android/testData", getAgpVersion());
  @Rule
  public RuleChain rule = RuleChain.outerRule(projectRule).around(new EdtRule());

  AndroidFacet facet;

  @Test
  public void testGetPackageForPath() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    facet = projectRule.androidFacet(":app");
    // Run assemble task to generate output listing file.
    String taskName = GradleAndroidModel.get(facet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    assertThat(taskName).isNotNull();
    projectRule.invokeTasks(taskName);

    File javaSrcDir = new File(AndroidRootUtil.findModuleRootFolderPath(facet.getModule()), "src/main/java");
    AndroidModulePaths androidModuleTemplate = Mockito.mock(AndroidModulePaths.class);
    Mockito.when(androidModuleTemplate.getSrcDirectory(null)).thenReturn(javaSrcDir);

    NamedModuleTemplate moduleTemplate = new NamedModuleTemplate("main", androidModuleTemplate);
    String defaultPackage = ProjectSystemUtil.getModuleSystem(facet).getPackageName();

    // Anything inside the Java src directory should return the "local package"
    assertThat(getPackageForPath(moduleTemplate, "app/src/main/java/google/simpleapplication")).isEqualTo("google.simpleapplication");
    assertThat(getPackageForPath(moduleTemplate, "app/src/main/java/google")).isEqualTo("google");

    // Anything outside the Java src directory should return the default package
    assertThat(getPackageForPath(moduleTemplate, "app/src/main/java")).isEqualTo(defaultPackage);
    assertThat(getPackageForPath(moduleTemplate, "app/src/main")).isEqualTo(defaultPackage);
    assertThat(getPackageForPath(moduleTemplate, "app/src")).isEqualTo(defaultPackage);
    assertThat(getPackageForPath(moduleTemplate, "app")).isEqualTo(defaultPackage);
    assertThat(getPackageForPath(moduleTemplate, "")).isEqualTo(defaultPackage);
    assertThat(getPackageForPath(moduleTemplate, "app/src/main/res")).isEqualTo(defaultPackage);
    assertThat(getPackageForPath(moduleTemplate, "app/src/main/res/layout")).isEqualTo(defaultPackage);
  }

  private String getPackageForPath(NamedModuleTemplate NamedModuleTemplate, String targetDirPath) {
    LocalFileSystem fs = LocalFileSystem.getInstance();
    VirtualFile targetDirectory = fs.refreshAndFindFileByPath(projectRule.getProject().getBasePath()).findFileByRelativePath(targetDirPath);

    return AndroidPackageUtils.getPackageForPath(facet, Lists.newArrayList(NamedModuleTemplate), targetDirectory);
  }
}
