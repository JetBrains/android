/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.common.experiments.BoolExperiment;
import java.util.Collection;

/** Temporary workaround for b/157683101 and b/154056735. */
class KotlinSyncAugmenter implements BlazeJavaSyncAugmenter {
  // TODO(b/246958300): remove once cl/487579008 is rolled out to all users

  // We cannot use genjars for kotlin target until https://youtrack.jetbrains.com/issue/KT-24309 get
  // fixed. Without generated jars, b/154056735 will be an issue, so we prefer to use class  jar
  // instead of interface jar. However, using class jar may affect indexing performance. The
  // experiment boolean provides users ability to switch between different jars.
  //  - CLASS_JAR: class and methods resolution works correctly but indexing may be slower
  //  - GENERATED_JAR: generated code cannot resolve methods in source files
  //  - INTERFACE_JAR: generated code may resolve methods incorrectly to class in
  //  META-INF/TRANSITIVE
  private static final BoolExperiment attachGenJar =
      new BoolExperiment("blaze.sync.kotlin.attach.genjar", true);
  private static final BoolExperiment attachClassJar =
      new BoolExperiment("blaze.sync.kotlin.attach.classjar", false);

  @Override
  public void addJarsForSourceTarget(
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ProjectViewSet projectViewSet,
      TargetIdeInfo target,
      Collection<BlazeJarLibrary> jars,
      Collection<BlazeJarLibrary> genJars) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)
        || !target.getKind().hasLanguage(LanguageClass.KOTLIN)) {
      return;
    }
    JavaIdeInfo javaInfo = target.getJavaIdeInfo();
    if (javaInfo == null || javaInfo.getFilteredGenJar() != null || shouldAttachGenJar(target)) {
      return;
    }
    // this is a temporary hack to include annotation processing genjars, by including *all* jars
    // produced by source targets. Currently, we get genjars of kotlin targets, but kotlin plugin
    // cannot resolve methods when genjars depend on sources of the project.
    javaInfo
        .getJars()
        .forEach(
            jar ->
                genJars.add(new BlazeJarLibrary(getFilteredLibraryArtifact(jar), target.getKey())));
  }

  /**
   * Without genjars, b/154056735 will be introduced. To resolve this, BlazeJarLibrary is forced to
   * use CLASS_JAR unless users opt out by setting attachClassJar to false.
   */
  private static LibraryArtifact getFilteredLibraryArtifact(LibraryArtifact jar) {
    if (!attachClassJar.getValue() || jar.getClassJar() == null || jar.getInterfaceJar() == null) {
      return jar;
    }
    return LibraryArtifact.builder()
        .setClassJar(jar.getClassJar())
        .addSourceJar(jar.getSourceJars().toArray(new ArtifactLocation[0]))
        .build();
  }

  @Override
  public boolean shouldAttachGenJar(TargetIdeInfo target) {
    return attachGenJar.getValue()
        || !target.getKind().hasLanguage(LanguageClass.KOTLIN)
        || target.getKind().getRuleType() == RuleType.UNKNOWN;
  }
}
