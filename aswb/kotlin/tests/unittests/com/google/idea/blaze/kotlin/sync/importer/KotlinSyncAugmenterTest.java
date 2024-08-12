/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.sync.importer;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.BlazeJavaSyncPlugin;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.kotlin.KotlinBlazeRules;
import com.google.idea.blaze.kotlin.sync.BlazeKotlinSyncPlugin;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeKotlinSyncPlugin} */
@RunWith(JUnit4.class)
public class KotlinSyncAugmenterTest extends BlazeTestCase {
  private final ErrorCollector errorCollector = new ErrorCollector();
  private ExtensionPointImpl<BlazeJavaSyncAugmenter> augmenters;
  private static final String FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT =
      "bazel-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    ExtensionPointImpl<BlazeSyncPlugin> syncPlugins =
        registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
    syncPlugins.registerExtension(new BlazeJavaSyncPlugin());
    syncPlugins.registerExtension(new BlazeKotlinSyncPlugin());
    BlazeContext context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);
    ExtensionPointImpl<Kind.Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new JavaBlazeRules());
    ep.registerExtension(new KotlinBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());

    MockExperimentService experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
    experimentService.setExperiment(
        new BoolExperiment("blaze.sync.kotlin.attach.genjar", true), false);
    experimentService.setExperiment(
        new BoolExperiment("blaze.sync.kotlin.attach.classjar", false), true);

    augmenters =
        registerExtensionPoint(BlazeJavaSyncAugmenter.EP_NAME, BlazeJavaSyncAugmenter.class);
    augmenters.registerExtension(new KotlinSyncAugmenter());
  }

  @Test
  public void testAddJarsForSourceTarget_attchClassJarForKotlinTarget() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.JAVA))
                    .add(
                        ListSection.builder(AdditionalLanguagesSection.KEY)
                            .add(LanguageClass.KOTLIN))
                    .build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

    TargetIdeInfo target =
        TargetIdeInfo.builder()
            .setLabel("//kt/example:source")
            .setBuildFile(source("kt/example/BUILD"))
            .setKind("kt_jvm_library_helper")
            .addSource(source("Source.kt"))
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addGeneratedJar(
                        LibraryArtifact.builder().setInterfaceJar(gen("generated.jar")))
                    .addJar(
                        LibraryArtifact.builder()
                            .setInterfaceJar(gen("full.jar"))
                            .setClassJar(gen("class.jar"))
                            .addSourceJar(gen("source.jar"))))
            .build();
    ArrayList<BlazeJarLibrary> genJars = new ArrayList<>();

    for (BlazeJavaSyncAugmenter augmenter : augmenters.getExtensionList()) {
      augmenter.addJarsForSourceTarget(
          workspaceLanguageSettings, projectViewSet, target, new ArrayList<>(), genJars);
    }
    assertThat(
            genJars.stream()
                .map(library -> library.libraryArtifact.jarForIntellijLibrary().getRelativePath()))
        .containsExactly("class.jar");
  }

  @Test
  public void testAddJarsForSourceTarget_noExtraGenJarListForJavaTarget() {
    ProjectViewSet projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.JAVA))
                    .add(
                        ListSection.builder(AdditionalLanguagesSection.KEY)
                            .add(LanguageClass.KOTLIN))
                    .build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

    TargetIdeInfo target =
        TargetIdeInfo.builder()
            .setLabel("//java/example:source")
            .setBuildFile(source("java/example/BUILD"))
            .setKind("java_library")
            .addSource(source("Source.java"))
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addGeneratedJar(
                        LibraryArtifact.builder().setInterfaceJar(gen("generated.jar")))
                    .addJar(
                        LibraryArtifact.builder()
                            .setInterfaceJar(gen("full.jar"))
                            .setClassJar(gen("class.jar"))
                            .addSourceJar(gen("source.jar"))))
            .build();
    ArrayList<BlazeJarLibrary> genJars = new ArrayList<>();

    for (BlazeJavaSyncAugmenter augmenter : augmenters.getExtensionList()) {
      augmenter.addJarsForSourceTarget(
          workspaceLanguageSettings, projectViewSet, target, new ArrayList<>(), genJars);
    }
    assertThat(genJars).isEmpty();
  }

  private static ArtifactLocation gen(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT)
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  private static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
