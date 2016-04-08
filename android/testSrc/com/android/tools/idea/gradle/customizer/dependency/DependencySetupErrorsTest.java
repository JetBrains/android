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
package com.android.tools.idea.gradle.customizer.dependency;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class DependencySetupErrorsTest {
  private DependencySetupErrors myErrors;

  @Before
  public void setUp() {
    myErrors = new DependencySetupErrors();
  }

  @Test
  public void testAddMissingModule() {
    myErrors.addMissingModule(":lib2", "app2", "library2.jar");
    myErrors.addMissingModule(":lib2", "app1", "library2.jar");
    myErrors.addMissingModule(":lib3", "app1", "library3.jar");
    myErrors.addMissingModule(":lib1", "app1", null);

    List<DependencySetupErrors.MissingModule> missingModules = myErrors.getMissingModules();
    assertThat(missingModules).hasSize(1);

    DependencySetupErrors.MissingModule missingModule = missingModules.get(0);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib1");
    assertThat(missingModule.dependentNames).containsExactly("app1");

    missingModules = myErrors.getMissingModulesWithBackupLibraries();
    assertThat(missingModules).hasSize(2);

    missingModule = missingModules.get(0);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib2");
    assertThat(missingModule.dependentNames).containsExactly("app1", "app2").inOrder();

    missingModule = missingModules.get(1);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib3");
    assertThat(missingModule.dependentNames).containsExactly("app1");
  }

  @Test
  public void testAddDependentOnModuleWithoutName() {
    myErrors.addMissingName("app2");
    myErrors.addMissingName("app2");
    myErrors.addMissingName("app1");
    assertThat(myErrors.getMissingNames()).containsExactly("app1", "app2").inOrder();
  }

  @Test
  public void testAddDependentOnLibraryWithoutBinaryPath() {
    myErrors.addMissingBinaryPath("app2");
    myErrors.addMissingBinaryPath("app2");
    myErrors.addMissingBinaryPath("app1");
    assertThat(myErrors.getDependentsOnLibrariesWithoutBinaryPath()).containsExactly("app1", "app2").inOrder();
  }
}