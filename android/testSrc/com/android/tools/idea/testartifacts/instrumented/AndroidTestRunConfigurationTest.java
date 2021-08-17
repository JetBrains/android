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
package com.android.tools.idea.testartifacts.instrumented;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import java.nio.file.Paths;
import java.util.List;

/**
 * Tests for {@link AndroidTestRunConfigurationTest}
 */
public class AndroidTestRunConfigurationTest extends AndroidGradleTestCase {
  private static final String TEST_APP_CLASS_NAME = "google.simpleapplication.ApplicationTest";

  /**
   * Check that there are no errors when the selected variant has a test artifact
   * @throws Exception
   */
  public void testCheckConfigurationNoErrors() throws Exception {
    System.out.println("isDumb: " + DumbService.isDumb(getProject()));
    System.out.println("JavaFullClassNameIndex: " + JavaFullClassNameIndex.getInstance().get(TEST_APP_CLASS_NAME.hashCode(), getProject(), GlobalSearchScope
      .projectScope(getProject())));

    loadProject(DYNAMIC_APP);
try {
    AndroidTestRunConfiguration androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), TEST_APP_CLASS_NAME);

    assertInstanceOf(androidTestRunConfiguration, AndroidTestRunConfiguration.class);

    AndroidModuleModel androidModel = AndroidModuleModel.get(myAndroidFacet);
    assertThat(androidModel).isNotNull();
    assertThat(androidModel.getSelectedVariant().getName()).isEqualTo("debug");

    List<ValidationError> errors = androidTestRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).isEmpty();
} finally {
    System.out.println("isDumb: " + DumbService.isDumb(getProject()));
    System.out.println("JavaFullClassNameIndex: " + JavaFullClassNameIndex.getInstance().get(TEST_APP_CLASS_NAME.hashCode(), getProject(), GlobalSearchScope
      .projectScope(getProject())));
    System.out.println("JavaFullClassNameIndex: " + JavaFullClassNameIndex.getInstance().get(TEST_APP_CLASS_NAME.hashCode(), getProject(), GlobalSearchScope
      .projectScope(getProject())));
    System.out.println("JavaFullClassNameIndex.containsKey: " + JavaFullClassNameIndex.getInstance().getAllKeys(getProject()).contains(TEST_APP_CLASS_NAME.hashCode()));

    System.out.println("findFileByNioPath: " + VirtualFileManager.getInstance().findFileByNioPath(Paths.get(
      getProject().getBasePath() + "/app/src/androidTest/java/google/simpleapplication/ApplicationTest.java")
    ));
    System.out.println("refreshAndFindFileByNioPath: " + VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Paths.get(
      getProject().getBasePath() + "/app/src/androidTest/java/google/simpleapplication/ApplicationTest.java")
    ));
    System.out.println("findFileByNioPath.filetype: " + VirtualFileManager.getInstance().findFileByNioPath(Paths.get(
      getProject().getBasePath() + "/app/src/androidTest/java/google/simpleapplication/ApplicationTest.java")
    ).getFileType());
    //ProjectDumper dumper = new ProjectDumper();
    //dumper.dump(getProject());
    //System.out.println(dumper);
}
  }

  /**
   * Check that there is an error when the selected variant does not have a test artifact
   * @throws Exception
   */
  public void testCheckConfigurationNoTestArtifact() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidTestRunConfiguration androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), TEST_APP_CLASS_NAME);
    assertInstanceOf(androidTestRunConfiguration, AndroidTestRunConfiguration.class);

    List<ValidationError> errors = androidTestRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getMessage()).matches("Active build variant \"(.*)\" does not have a test artifact.");
  }
}
