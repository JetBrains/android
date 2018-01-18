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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.hamcrest.Matcher;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public class PsModuleTest extends AndroidGradleTestCase {


  private static final String ORIGINAL_GUAVA_COORDINATES = "com.google.guava:guava:19.0";
  private static final String UPDATED_GUAVA_COORDINATES = "com.google.guava:guava:20.0";

  public void testApplyChanges() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION);
    PsProject psProject = new PsProject(getProject());
    PsAndroidModule psAppModule = (PsAndroidModule)psProject.findModuleByName("app");
    Document buildFileDocument = getDocument();
    assumeThat(buildFileDocument.getText(), not(containsString(UPDATED_GUAVA_COORDINATES)));

    PsLibraryAndroidDependency dependency = psAppModule.findLibraryDependency(ORIGINAL_GUAVA_COORDINATES);
    dependency.setVersion("20.0");
    assertThat(buildFileDocument.getText(), not(containsString(UPDATED_GUAVA_COORDINATES)));
    assertThat(psAppModule.isModified(), is(true));
    psAppModule.applyChanges();

    assertThat(psAppModule.isModified(), is(false));
    assertThat(buildFileDocument.getText(), containsString(UPDATED_GUAVA_COORDINATES));
  }

  public void testLocalRepositories() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION);
    PsProject psProject = new PsProject(getProject());
    PsAndroidModule psAppModule = (PsAndroidModule)psProject.findModuleByName("app");
    assertThat(psAppModule.getParsedModel().repositories().repositories(), hasItem(instanceOf(MavenRepositoryModel.class)));
    List<String> mavenRepositories =
      psAppModule.getParsedModel().repositories().repositories().stream()
        .filter(it -> it.getType() == RepositoryModel.RepositoryType.MAVEN)
        .map(it -> ((MavenRepositoryModel)(it)).url().value())
        .collect(toList());
    Iterable<Matcher<? super String>> localRepositoryMatchers =
      AndroidGradleTests
        .getLocalRepositoryDirectories()
        .stream()
        .map(v -> is(v.toURI().toString()))
        .collect(toList());
    assertThat(mavenRepositories, hasItem(anyOf(localRepositoryMatchers)));
  }

  public void testGetVariables() throws Exception {
    loadProject(TestProjectPaths.PSD_SAMPLE);
    PsProject psProject = new PsProject(getProject());
    PsAndroidModule psAppModule = (PsAndroidModule)psProject.findModuleByName("app");
    List<PsVariable> variables = psAppModule.getVariables();
    assertThat(variables.size(), is(4));
    assertThat(variables.stream().map(PsVariable::getName).collect(toList()),
               hasItems("myVariable", "variable1", "anotherVariable", "moreVariable"));
  }

  private Document getDocument() {
    VirtualFile buildFile = myFixture.getProject().getBaseDir().findFileByRelativePath("app/build.gradle");
    return FileDocumentManager.getInstance().getDocument(buildFile);
  }
}