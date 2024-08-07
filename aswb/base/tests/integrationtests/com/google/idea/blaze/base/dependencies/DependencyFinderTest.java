/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.dependencies;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.testing.ServiceHelper;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link DependencyFinder}. */
@RunWith(JUnit4.class)
public final class DependencyFinderTest extends BlazeIntegrationTestCase {

  @Mock private BlazeProjectDataManager mockProjectDataManager;

  @Before
  public void initTest() {
    MockitoAnnotations.initMocks(this);
    ServiceHelper.registerProjectService(
        testFixture.getProject(),
        BlazeProjectDataManager.class,
        mockProjectDataManager,
        getTestRootDisposable());
  }

  @Test
  public void getCompileTimeDependencyTargets() throws Exception {
    ImmutableList<TargetIdeInfo> ideInfos =
        ImmutableList.of(
            createTargetIdeInfo(
                "//package1:target1",
                ImmutableList.of("//package2:compiletimedep1", "//package3:compiletimedep2"),
                ImmutableList.of("//package2:runtimedep1", "//package4:runtimedep2")),
            createTargetIdeInfo(
                "//package3:target2",
                ImmutableList.of(
                    "//package2:compiletimedep1",
                    "//package3:compiletimedep3",
                    "//package3:compiletimedep4"),
                ImmutableList.of("//package1:runtimedep3", "//package5:runtimedep4")),
            createTargetIdeInfo(
                "//package2:compiletimedep1",
                ImmutableList.of("//package3:compiletimedep2", "//package4:compiletimedep5"),
                ImmutableList.of("//package2:runtimedep5", "//package4:runtimedep6")),
            createTargetIdeInfo(
                "//package3:compiletimedep2",
                ImmutableList.of("//package3:compiletimedep3", "//package3:compiletimedep4"),
                ImmutableList.of("//package4:runtimedep2")),
            createTargetIdeInfo(
                "//package3:compiletimedep3", ImmutableList.of(), ImmutableList.of()),
            createTargetIdeInfo(
                "//package3:compiletimedep4",
                ImmutableList.of("//package2:compiletimedep1", "//package3:compiletimedep5"),
                ImmutableList.of("//package1:runtimedep3")),
            createTargetIdeInfo(
                "//package4:compiletimedep5",
                ImmutableList.of("//package3:compiletimedep2"),
                ImmutableList.of()));
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder()
            .setTargetMap(
                new TargetMap(
                    ideInfos.stream()
                        .collect(
                            ImmutableMap.toImmutableMap(
                                TargetIdeInfo::getKey, ideInfo -> ideInfo))))
            .build();

    when(mockProjectDataManager.getBlazeProjectData()).thenReturn(projectData);

    List<TargetInfo> actual =
        DependencyFinder.getCompileTimeDependencyTargets(
            testFixture.getProject(), Label.create("//package3:target2"));

    assertThat(actual)
        .containsExactly(
            TargetInfo.builder(Label.create("//package2:compiletimedep1"), "proto_library").build(),
            TargetInfo.builder(Label.create("//package3:compiletimedep3"), "proto_library").build(),
            TargetInfo.builder(Label.create("//package3:compiletimedep4"), "proto_library")
                .build());
  }

  private static TargetIdeInfo createTargetIdeInfo(
      String label,
      ImmutableList<String> compileTimeDependencies,
      ImmutableList<String> runtimeDependencies) {
    TargetIdeInfo.Builder ideInfo =
        TargetIdeInfo.builder().setLabel(label).setKind("proto_library");
    compileTimeDependencies.stream().forEach(dependency -> ideInfo.addDependency(dependency));
    runtimeDependencies.stream().forEach(dependency -> ideInfo.addRuntimeDep(dependency));
    return ideInfo.build();
  }
}
