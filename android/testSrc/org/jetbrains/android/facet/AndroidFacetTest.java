/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.facet;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import org.jetbrains.android.AndroidTestCase;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link AndroidFacet}.
 */
public class AndroidFacetTest extends AndroidTestCase {
  private IdeaAndroidProject myAndroidProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = createMock(IdeaAndroidProject.class);
  }

  public void testSetIdeaAndroidProject() {
    AndroidFacet.GradleProjectAvailableListener listener1 = createMock(AndroidFacet.GradleProjectAvailableListener.class);
    listener1.gradleProjectAvailable(myAndroidProject);
    expectLastCall().once();

    AndroidFacet.GradleProjectAvailableListener listener2 = createMock(AndroidFacet.GradleProjectAvailableListener.class);
    listener2.gradleProjectAvailable(myAndroidProject);
    expectLastCall().times(2);

    replay(listener1, listener2);

    myFacet.addListener(listener1);

    // This should notify listener1.
    myFacet.setIdeaAndroidProject(myAndroidProject);

    // This should notify listener2.
    myFacet.addListener(listener2);

    myFacet.removeListener(listener1);

    // This should notify listener2.
    myFacet.setIdeaAndroidProject(myAndroidProject);

    verify(listener1, listener2);
  }
}
