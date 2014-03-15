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
package com.android.tools.idea.gradle.project;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.File;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverTest extends TestCase {
  public void testModuleDirPathWithFoundProject() {
    GradleBuild build = createMock(GradleBuild.class);
    BasicGradleProject project1 = createMock(BasicGradleProject.class);
    BasicGradleProject project2 = createMock(BasicGradleProject.class);

    ImmutableDomainObjectSet<? extends BasicGradleProject> projects =
      new ImmutableDomainObjectSet<BasicGradleProject>(Lists.newArrayList(project1, project2));

    build.getProjects();
    expectLastCall().andStubReturn(projects);

    expect(project1.getPath()).andStubReturn(":project1");
    expect(project2.getPath()).andStubReturn(":project2");

    File moduleDirPath = new File("project2");
    expect(project2.getProjectDirectory()).andStubReturn(moduleDirPath);

    replay(build, project1, project2);

    assertSame(moduleDirPath, AndroidGradleProjectResolver.getModuleDirPath(build, ":project2"));

    verify(build, project1, project2);
  }

  public void testModuleDirPathWithNotFoundProject() {
    GradleBuild build = createMock(GradleBuild.class);
    BasicGradleProject project1 = createMock(BasicGradleProject.class);

    ImmutableDomainObjectSet<? extends BasicGradleProject> projects =
      new ImmutableDomainObjectSet<BasicGradleProject>(Lists.newArrayList(project1));

    build.getProjects();
    expectLastCall().andStubReturn(projects);

    expect(project1.getPath()).andStubReturn(":project1");

    replay(build, project1);

    assertNull(AndroidGradleProjectResolver.getModuleDirPath(build, ":project2"));

    verify(build, project1);
  }
}
