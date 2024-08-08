/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.idea.blaze.base.sync;

import static org.junit.Assert.fail;

import com.google.devtools.build.lib.view.proto.Deps;
import com.google.idea.blaze.base.TestFileSystem;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Test utility for simulating the jdeps files that would normally be exposed by the build system
 * for Java targets.
 */
public class JdepsFileWriter {
  private final TestFileSystem fileSystem;

  public JdepsFileWriter(TestFileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  /**
   * For each target in the given target map whose JavaIdeInfo specifies a path to a jdeps file,
   * this method constructs the appropriate jdeps proto for the target and writes it to that file.
   */
  public static void writeDefaultJdepsFiles(
      String execRoot, TestFileSystem fileSystem, TargetMap targetMap) {
    JdepsFileWriter jdepsFileWriter = new JdepsFileWriter(fileSystem);
    targetMap
        .targets()
        .forEach(target -> jdepsFileWriter.writeJdepsFile(execRoot, targetMap, target));
  }

  /**
   * Constructs a jdeps proto for the given blaze target and writes it to the jdeps file specified
   * in the target's JavaIdeInfo.
   */
  private void writeJdepsFile(String execRoot, TargetMap targetMap, TargetIdeInfo target) {
    if (target.getJavaIdeInfo() == null || target.getJavaIdeInfo().getJdepsFile() == null) {
      return;
    }
    Deps.Dependencies jdeps = getJdeps(targetMap, target);
    ArtifactLocation jdepsArtifact = target.getJavaIdeInfo().getJdepsFile();
    VirtualFile jdepsFile =
        fileSystem.createFile(execRoot + "/" + jdepsArtifact.getExecutionRootRelativePath());
    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(
        () ->
            application.runWriteAction(
                () -> {
                  try (OutputStream jdepsOutputStream = jdepsFile.getOutputStream(this)) {
                    jdeps.writeTo(jdepsOutputStream);
                  } catch (IOException e) {
                    fail(e.getMessage());
                  }
                }));
  }

  private static Deps.Dependencies getJdeps(TargetMap targetMap, TargetIdeInfo target) {
    Deps.Dependencies.Builder jdeps =
        Deps.Dependencies.newBuilder().setRuleLabel(target.getKey().getLabel().toString());
    target.getDependencies().stream()
        .map(Dependency::getTargetKey)
        .map(targetMap::get)
        .filter(Objects::nonNull)
        .flatMap(JdepsFileWriter::getJars)
        .map(ArtifactLocation::getRelativePath)
        .map(
            jarPath ->
                Deps.Dependency.newBuilder()
                    .setKind(Deps.Dependency.Kind.EXPLICIT)
                    .setPath(jarPath))
        .forEach(jdeps::addDependency);
    return jdeps.build();
  }

  private static Stream<ArtifactLocation> getJars(TargetIdeInfo target) {
    if (target.getJavaIdeInfo() == null) {
      return Stream.of();
    }
    return target.getJavaIdeInfo().getJars().stream()
        .flatMap(jarArtifact -> Stream.of(jarArtifact.getClassJar(), jarArtifact.getInterfaceJar()))
        .filter(Objects::nonNull);
  }
}
