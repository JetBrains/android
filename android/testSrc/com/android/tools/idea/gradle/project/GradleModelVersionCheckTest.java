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
package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidProject;
import junit.framework.TestCase;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link GradleModelVersionCheck}.
 */
public class GradleModelVersionCheckTest extends TestCase {
  private AndroidProject myProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProject = createMock(AndroidProject.class);
  }

  public void testIsSupportedVersionWithNullVersion() {
    expect(myProject.getModelVersion()).andReturn(null);
    replay(myProject);

    assertFalse(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithEmptyVersion() {
    expect(myProject.getModelVersion()).andReturn("");
    replay(myProject);

    assertFalse(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithOldVersion() {
    expect(myProject.getModelVersion()).andReturn("0.4.3");
    replay(myProject);

    assertFalse(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithMinimumSupportedVersion() {
    expect(myProject.getModelVersion()).andReturn("0.5.0");
    replay(myProject);

    assertTrue(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithSupportedVersionWithMacroGreaterThanZero() {
    expect(myProject.getModelVersion()).andReturn("0.5.1");
    replay(myProject);

    assertTrue(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithSupportedVersionWithMinorGreaterThanFive() {
    expect(myProject.getModelVersion()).andReturn("0.6.0");
    replay(myProject);

    assertTrue(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithSupportedVersionWithMajorGreaterThanZero() {
    expect(myProject.getModelVersion()).andReturn("1.0.0");
    replay(myProject);

    assertTrue(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithSupportedSnapshotVersion() {
    expect(myProject.getModelVersion()).andReturn("0.5.0-SNAPSHOT");
    replay(myProject);

    assertTrue(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }

  public void testIsSupportedVersionWithUnparseableVersion() {
    expect(myProject.getModelVersion()).andReturn("Hello");
    replay(myProject);

    assertFalse(GradleModelVersionCheck.isSupportedVersion(myProject));

    verify(myProject);
  }
}
