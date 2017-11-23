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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

import java.util.Arrays;

public final class CreateMultiRootResourceFileActionTest extends AndroidTestCase {
  private CreateMultiRootResourceFileAction myAction;
  private TestProjectSystem myTestProjectSystem;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myAction = Mockito.spy(new CreateMultiRootResourceFileAction("Layout", ResourceFolderType.LAYOUT));
    myTestProjectSystem = new TestProjectSystem();
    PlatformTestUtil.registerExtension(Extensions.getArea(myModule.getProject()), ProjectSystemUtil.getEP_NAME(),
                                       myTestProjectSystem, getTestRootDisposable());
  }

  public void testGetAllowedTagNamesModuleDoesntDependOnConstaintLayout() {
    myAction.getAllowedTagNames(myFacet);
    assertEquals(SdkConstants.LINEAR_LAYOUT, myAction.getDefaultRootTag());
  }

  /*public void testGetAllowedTagNamesModuleDependsOnConstraintLayout() {
    myTestProjectSystem.addDependency(GoogleMavenArtifactId.CONSTRAINT_LAYOUT, myFacet.getModule(), new GradleVersion(1, 1));
    Mockito.when(myAction.getPossibleRoots(myFacet)).thenReturn(Arrays.asList(SdkConstants.LINEAR_LAYOUT, SdkConstants.CONSTRAINT_LAYOUT));

    myAction.getAllowedTagNames(myFacet);
    assertEquals(SdkConstants.CONSTRAINT_LAYOUT, myAction.getDefaultRootTag());
  }*/
}
