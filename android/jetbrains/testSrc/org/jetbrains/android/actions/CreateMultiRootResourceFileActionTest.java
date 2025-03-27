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
package org.jetbrains.android.actions;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import java.util.Arrays;
import java.util.Collections;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

public final class CreateMultiRootResourceFileActionTest extends AndroidTestCase {
  private CreateMultiRootResourceFileAction myAction;
  private TestProjectSystem myTestProjectSystem;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myAction = Mockito.spy(new CreateMultiRootResourceFileAction("Layout", ResourceFolderType.LAYOUT));
    myTestProjectSystem = new TestProjectSystem(getProject(), Collections.emptyList());
    myTestProjectSystem.useInTests();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myAction = null;
  }

  public void testGetDefaultRootTag_ModuleDoesntDependOnConstraintLayout() {
    assertEquals(SdkConstants.LINEAR_LAYOUT, myAction.getDefaultRootTag(myFacet.getModule()));
  }

  public void testGetDefaultRootTag_ModuleDependsOnConstraintLayout() {
    myTestProjectSystem.addDependency(GoogleMavenArtifactId.CONSTRAINT_LAYOUT, myFacet.getModule(), new GradleVersion(1, 1));
    Mockito.when(myAction.getPossibleRoots(myFacet))
      .thenReturn(Arrays.asList(SdkConstants.LINEAR_LAYOUT, AndroidXConstants.CONSTRAINT_LAYOUT.oldName()));

    assertEquals(AndroidXConstants.CONSTRAINT_LAYOUT.oldName(), myAction.getDefaultRootTag(myFacet.getModule()));
  }

  public void testGetDefaultRootTag_ModuleDependsOnAndroidXConstraintLayout() {
    myTestProjectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_CONSTRAINTLAYOUT, myFacet.getModule(), new GradleVersion(1, 1));
    Mockito.when(myAction.getPossibleRoots(myFacet))
      .thenReturn(Arrays.asList(SdkConstants.LINEAR_LAYOUT, AndroidXConstants.CONSTRAINT_LAYOUT.defaultName()));

    assertEquals(AndroidXConstants.CONSTRAINT_LAYOUT.newName(), myAction.getDefaultRootTag(myFacet.getModule()));
  }
}
