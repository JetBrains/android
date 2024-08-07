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
package com.google.idea.blaze.base.ideinfo;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link JavaIdeInfo}. */
@RunWith(JUnit4.class)
public class JavaIdeInfoTest {

  @Test
  public void testUnchangedFromSerializationRoundTrip() {
    IntellijIdeInfo.JavaIdeInfo proto =
        IntellijIdeInfo.JavaIdeInfo.newBuilder()
            .addSources(artifactLocation("com/google/lib/Source.java"))
            .addSources(artifactLocation("com/google/other/Other.java"))
            .addJars(
                IntellijIdeInfo.LibraryArtifact.newBuilder()
                    .setJar(artifactLocation("jar.jar"))
                    .setInterfaceJar(artifactLocation("jar.ijar"))
                    .addSourceJars(artifactLocation("jar.srcjar")))
            .addGeneratedJars(
                IntellijIdeInfo.LibraryArtifact.newBuilder()
                    .setJar(artifactLocation("genjar.jar"))
                    .setInterfaceJar(artifactLocation("genjar.ijar"))
                    .addSourceJars(artifactLocation("genjar.srcjar")))
            .build();

    assertThat(JavaIdeInfo.fromProto(proto).toProto()).isEqualTo(proto);
  }

  private static Common.ArtifactLocation artifactLocation(String relativePath) {
    return Common.ArtifactLocation.newBuilder().setRelativePath(relativePath).build();
  }
}
