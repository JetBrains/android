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

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.structure.configurables.CachingRepositorySearchFactory;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import java.util.List;
import org.hamcrest.Matcher;

public class PsModuleTest extends AndroidGradleTestCase {


  private static final String GUAVA_GROUP = "com.google.guava";
  private static final String GUAVA_NAME = "guava";
  private static final String UPDATED_GUAVA_COORDINATES = "com.google.guava:guava:20.0";

  public void testApplyChanges() throws Exception {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION);
    PsProject psProject = new PsProjectImpl(getProject(), new CachingRepositorySearchFactory());
    PsAndroidModule psAppModule = (PsAndroidModule)psProject.findModuleByName("app");
    Document buildFileDocument = getDocument();
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
  }

  public void testLocalRepositories() throws Exception {
    if (AndroidGradleTests.shouldUseRemoteRepositories()) {
      assert !IdeInfo.getInstance().isAndroidStudio();
      return; // IDEA does not use local repositories during testing
    }

    loadProject(TestProjectPaths.SIMPLE_APPLICATION);
    PsProject psProject = new PsProjectImpl(getProject(), new CachingRepositorySearchFactory());
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
  }

  private Document getDocument() {
    VirtualFile buildFile = PlatformTestUtil.getOrCreateProjectBaseDir(myFixture.getProject()).findFileByRelativePath("app/build.gradle");
    return FileDocumentManager.getInstance().getDocument(buildFile);
  }
}
