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
package com.google.idea.blaze.python.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonSrcsVersion;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonVersion;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.PyIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.python.PythonBlazeRules;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazePythonSyncPlugin}. */
@RunWith(JUnit4.class)
public class BlazePythonSyncPluginTest extends BlazeTestCase {

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class)
        .registerExtension(new PythonBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(QuerySyncSettings.class, new QuerySyncSettings());
  }

  @Test
  public void testNoCompatibleSdk() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py2")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py2.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY2)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY2ONLY)))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py3")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py3.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY3)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY3ONLY)))
            .build();
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder().setTargetMap(targetMap).build();

    ImmutableList<PythonVersion> versions =
        BlazePythonSyncPlugin.suggestPythonVersions(blazeProjectData);
    assertThat(versions).isEmpty();
  }

  @Test
  public void testCompatiblePy2Sdk() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py2")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py2.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY2)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY2ONLY)))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py3")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py3.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY3)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY2AND3)))
            .build();
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder().setTargetMap(targetMap).build();

    ImmutableList<PythonVersion> versions =
        BlazePythonSyncPlugin.suggestPythonVersions(blazeProjectData);
    assertThat(versions).containsExactly(PythonVersion.PY2);
  }

  @Test
  public void testCompatiblePy3Sdk() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py2")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py2.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY2)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY2AND3)))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py3")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py3.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY3)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY3ONLY)))
            .build();
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder().setTargetMap(targetMap).build();

    ImmutableList<PythonVersion> versions =
        BlazePythonSyncPlugin.suggestPythonVersions(blazeProjectData);
    assertThat(versions).containsExactly(PythonVersion.PY3);
  }

  @Test
  public void testCompatiblePy2And3Sdk() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py2")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py2.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY2)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY2AND3)))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//python:main_py3")
                    .setKind("py_binary")
                    .addSource(sourceRoot("test/main_py3.py"))
                    .setPyInfo(
                        PyIdeInfo.builder()
                            .setPythonVersion(PythonVersion.PY3)
                            .setSrcsVersion(PythonSrcsVersion.SRC_PY2AND3)))
            .build();
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder().setTargetMap(targetMap).build();

    List<PythonVersion> versions = BlazePythonSyncPlugin.suggestPythonVersions(blazeProjectData);
    assertThat(versions).containsExactly(PythonVersion.PY3, PythonVersion.PY2).inOrder();
  }

  private ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
