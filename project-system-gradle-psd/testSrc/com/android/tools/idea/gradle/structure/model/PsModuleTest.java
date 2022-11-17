/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model;

import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static java.util.stream.Collectors.toList;
import static junit.framework.Assert.assertNotNull;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.gradle.repositories.search.CachingRepositorySearchFactory;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import java.io.IOException;
import java.util.List;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class PsModuleTest {

  @Rule
  public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();

  private static final String GUAVA_GROUP = "com.google.guava";
  private static final String GUAVA_NAME = "guava";
  private static final String UPDATED_GUAVA_COORDINATES = "com.google.guava:guava:20.0";

  @Test
  public void testApplyChanges() throws Exception {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, (project) -> {
      PsProject psProject = new PsProjectImpl(project, new CachingRepositorySearchFactory());
      PsAndroidModule psAppModule = (PsAndroidModule)psProject.findModuleByName("app");
      Document buildFileDocument = getDocument(project);
      assumeThat(buildFileDocument.getText(), not(containsString(UPDATED_GUAVA_COORDINATES)));

      PsLibraryAndroidDependency dependency =
        psAppModule.getDependencies().findLibraryDependencies(GUAVA_GROUP, GUAVA_NAME).stream().findFirst().orElse(null);
      assertThat(dependency, notNullValue());
      psAppModule.setLibraryDependencyVersion(dependency.getSpec(), "api", "20.0", false);
      assertThat(buildFileDocument.getText(), not(containsString(UPDATED_GUAVA_COORDINATES)));
      assertThat(psAppModule.isModified(), is(true));
      psAppModule.applyChanges();

      assertThat(psAppModule.isModified(), is(false));
      assertThat(buildFileDocument.getText(), containsString(UPDATED_GUAVA_COORDINATES));
      return null;
    });
  }

  @Test
  public void testLocalRepositories() throws Exception {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, (project) -> {
      PsProject psProject = new PsProjectImpl(project, new CachingRepositorySearchFactory());
      PsAndroidModule psAppModule = (PsAndroidModule)psProject.findModuleByName("app");
      assertThat(psAppModule.getParsedModel().repositories().repositories(), hasItem(instanceOf(MavenRepositoryModel.class)));
      List<String> mavenRepositories =
        psAppModule.getParsedModel().repositories().repositories().stream()
          .filter(it -> it.getType() == RepositoryModel.RepositoryType.MAVEN)
          .map(it -> ((MavenRepositoryModel)(it)).url().toString())
          .collect(toList());
      Iterable<Matcher<? super String>> localRepositoryMatchers =
        AndroidGradleTests
          .getLocalRepositoryDirectories()
          .stream()
          .map(v -> is(v.toURI().toString()))
          .collect(toList());
      assertThat(mavenRepositories, hasItem(anyOf(localRepositoryMatchers)));
      return null;
    });
  }

  @Test
  public void testToArtifactRepository() throws IOException {
    assertNotNull(PsModuleKt.toArtifactRepository(createLocalMavenRepository("file://")));
    assertNotNull(PsModuleKt.toArtifactRepository(createLocalMavenRepository("file:")));
    assertNotNull(PsModuleKt.toArtifactRepository(createLocalMavenRepository("")));
  }

  @NotNull
  private MavenRepositoryModel createLocalMavenRepository(@NotNull String protocol) throws IOException {
    String tempDir = FileUtil.generateRandomTemporaryPath().getAbsolutePath();
    MavenRepositoryModel model = mock(MavenRepositoryModel.class);
    ResolvedPropertyModel urlModel = mock(ResolvedPropertyModel.class);
    ResolvedPropertyModel nameModel = mock(ResolvedPropertyModel.class);
    when(model.name()).thenReturn(nameModel);
    when(model.url()).thenReturn(urlModel);
    when(model.getType()).thenReturn(RepositoryModel.RepositoryType.MAVEN);
    when(urlModel.forceString()).thenReturn(protocol + tempDir);
    when(nameModel.forceString()).thenReturn("");
    return model;
  }

  private Document getDocument(@NotNull Project project) {
    VirtualFile buildFile = PlatformTestUtil.getOrCreateProjectBaseDir(project).findFileByRelativePath("app/build.gradle");
    return FileDocumentManager.getInstance().getDocument(buildFile);
  }
}
