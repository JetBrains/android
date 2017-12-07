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
package com.android.tools.idea.projectsystem;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.projectsystem.gradle.GradleDependencyVersion;
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem;
import com.android.tools.idea.templates.IdeGoogleMavenRepository;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mockito;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;


public class GradleModuleSystemTest extends IdeaTestCase {
  private IdeComponents myIdeComponents;
  private GradleDependencyManager myGradleDependencyManager;
  private GradleModuleSystem myGradleModuleSystem;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(myProject);
    myGradleDependencyManager = myIdeComponents.mockProjectService(GradleDependencyManager.class);
    myGradleModuleSystem = new GradleModuleSystem(myModule);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testAddDependency() {
    GoogleMavenArtifactId toAdd = GoogleMavenArtifactId.CONSTRAINT_LAYOUT;

    myGradleModuleSystem.addDependency(toAdd, null);

    Mockito.verify(myGradleDependencyManager, times(1))
      .addDependencies(myModule, Collections.singletonList(getLatestCoordinateForArtifactId(toAdd)), null);
  }

  public void testAddDependencyWithBadVersion() {
    GoogleMavenArtifactId toAdd = GoogleMavenArtifactId.CONSTRAINT_LAYOUT;
    GoogleMavenArtifactVersion version = new GradleDependencyVersion(null);

    try {
      myGradleModuleSystem.addDependency(toAdd, version);
      fail("addDependencies should have thrown an exception.");
    }
    catch (DependencyManagementException e) {
      assertThat(e.getErrorCode()).isEqualTo(DependencyManagementException.ErrorCodes.INVALID_ARTIFACT);
    }
  }

  private GradleCoordinate getLatestCoordinateForArtifactId(GoogleMavenArtifactId id) {
    GradleCoordinate wildCardCoordinate = GradleCoordinate.parseCoordinateString(id.toString() + ":+");
    GradleVersion version = IdeGoogleMavenRepository.INSTANCE.findVersion(wildCardCoordinate, null, false);
    return GradleCoordinate.parseCoordinateString(id.toString() + ":" + version);
  }
}
