/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidFrameworkDetector}.
 */
public class AndroidFrameworkDetectorTest extends IdeaTestCase {
  @Mock private FrameworkDetectionContext myContext;
  @Mock GradleProjectInfo myProjectInfo;

  private AndroidFrameworkDetector myDetector;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    when(myContext.getProject()).thenReturn(getProject());
    myDetector = new AndroidFrameworkDetector();
  }

  public void testDetectWithGradleProject() {
    IdeComponents.replaceService(myProject, GradleProjectInfo.class, myProjectInfo);
    when(myProjectInfo.isBuildWithGradle()).thenReturn(true);

    List<? extends DetectedFrameworkDescription> descriptions = myDetector.detect(Collections.emptyList(), myContext);
    assertThat(descriptions).isEmpty();
  }

  public void testDetectWithProjectWithBuildFile() {
    IdeComponents.replaceService(myProject, GradleProjectInfo.class, myProjectInfo);
    when(myProjectInfo.isBuildWithGradle()).thenReturn(false);
    when(myProjectInfo.hasTopLevelGradleBuildFile()).thenReturn(true);

    List<? extends DetectedFrameworkDescription> descriptions = myDetector.detect(Collections.emptyList(), myContext);
    assertThat(descriptions).isEmpty();
  }
}