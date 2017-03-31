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
package com.android.tools.idea.gradle.project.model;

import com.android.java.model.ArtifactModel;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ArtifactModuleModelFactory}.
 */
public class ArtifactModuleModelFactoryTest {
  @Mock private ArtifactModel myArtifactModel;
  @Mock private GradleProject myGradleProject;
  private ArtifactModuleModelFactory myArtifactModuleModelFactory;

  @Before
  public void setUpClass() {
    initMocks(this);
    myArtifactModuleModelFactory = new ArtifactModuleModelFactory();
  }

  @Test
  public void verifyJavaModuleModel() {
    File buildDir = new File("/mock/project/build");
    File projectDir = new File("/mock/project");
    String moduleName = "jarAarModule";
    Map<String, Set<File>> artifactsByConfiguration = new HashMap<>();
    artifactsByConfiguration.put("default", Collections.emptySet());

    when(myArtifactModel.getName()).thenReturn(moduleName);
    when(myArtifactModel.getArtifactsByConfiguration()).thenReturn(artifactsByConfiguration);

    when(myGradleProject.getBuildDirectory()).thenReturn(buildDir);
    when(myGradleProject.getProjectDirectory()).thenReturn(projectDir);
    doReturn(ImmutableDomainObjectSet.of(Collections.emptyList())).when(myGradleProject).getTasks();

    JavaModuleModel javaModuleModel = myArtifactModuleModelFactory.create(myGradleProject, myArtifactModel);
    assertThat(javaModuleModel.getModuleName()).isEqualTo(moduleName);
    assertThat(javaModuleModel.isAndroidModuleWithoutVariants()).isFalse();
    assertThat(javaModuleModel.getBuildFolderPath()).isEqualTo(buildDir);
    assertThat(javaModuleModel.getJavaLanguageLevel()).isNull();
    assertThat(javaModuleModel.getContentRoots()).hasSize(1);
    assertThat(javaModuleModel.getArtifactsByConfiguration()).isEqualTo(artifactsByConfiguration);
    assertThat(javaModuleModel.getConfigurations()).containsExactly("default");
    assertThat(javaModuleModel.getJarLibraryDependencies()).isEmpty();
    assertThat(javaModuleModel.getJavaModuleDependencies()).isEmpty();
  }
}
