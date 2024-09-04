/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.workspace;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.aspect.Common;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link ArtifactLocationDecoder}. */
@RunWith(JUnit4.class)
public class ArtifactLocationDecoderTest extends BlazeTestCase {

  private static final String OUTPUT_BASE = "/path/to/_blaze_user/1234bf129e";
  private static final String EXECUTION_ROOT = OUTPUT_BASE + "/execroot/my_proj";

  @Test
  public void testGeneratedArtifact() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment("/blaze-out/bin")
            .setRelativePath("com/google/Bla.java")
            .setIsSource(false)
            .build();

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(EXECUTION_ROOT + "/blaze-out/bin/com/google/Bla.java");
  }

  @Test
  public void testExternalSourceArtifactOldFormat() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.fromProto(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("external/repo_name/com/google/Bla.java")
                .setIsSource(true)
                .setIsExternal(true)
                .build());

    assertThat(artifactLocation.getRelativePath()).isEqualTo("com/google/Bla.java");
    assertThat(artifactLocation.getExecutionRootRelativePath())
        .isEqualTo("external/repo_name/com/google/Bla.java");

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(EXECUTION_ROOT + "/external/repo_name/com/google/Bla.java");
  }

  @Test
  public void testExternalDerivedArtifactOldFormat() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.fromProto(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("external/repo_name/com/google/Bla.java")
                .setRootExecutionPathFragment("blaze-out/crosstool/bin")
                .setIsSource(false)
                .setIsExternal(true)
                .build());

    assertThat(artifactLocation.getRelativePath()).isEqualTo("com/google/Bla.java");
    assertThat(artifactLocation.getExecutionRootRelativePath())
        .isEqualTo("blaze-out/crosstool/bin/external/repo_name/com/google/Bla.java");

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(
            EXECUTION_ROOT + "/blaze-out/crosstool/bin/external/repo_name/com/google/Bla.java");
  }

  @Test
  public void testExternalSourceArtifactNewFormat() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.fromProto(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("com/google/Bla.java")
                .setRootExecutionPathFragment("../repo_name")
                .setIsSource(true)
                .setIsExternal(true)
                .setIsNewExternalVersion(true)
                .build());

    assertThat(artifactLocation.getRelativePath()).isEqualTo("com/google/Bla.java");
    assertThat(artifactLocation.getExecutionRootRelativePath())
        .isEqualTo("../repo_name/com/google/Bla.java");

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(OUTPUT_BASE + "/execroot/repo_name/com/google/Bla.java");
  }

  @Test
  public void testExternalDerivedArtifactNewFormat() {
    ArtifactLocation artifactLocation =
        ArtifactLocation.fromProto(
            Common.ArtifactLocation.newBuilder()
                .setRelativePath("com/google/Bla.java")
                .setRootExecutionPathFragment("../repo_name/blaze-out/crosstool/bin")
                .setIsSource(false)
                .setIsNewExternalVersion(true)
                .build());

    assertThat(artifactLocation.getRelativePath()).isEqualTo("com/google/Bla.java");
    assertThat(artifactLocation.getExecutionRootRelativePath())
        .isEqualTo("../repo_name/blaze-out/crosstool/bin/com/google/Bla.java");

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            BlazeInfo.createMockBlazeInfo(
                OUTPUT_BASE,
                EXECUTION_ROOT,
                EXECUTION_ROOT + "/blaze-out/crosstool/bin",
                EXECUTION_ROOT + "/blaze-out/crosstool/genfiles",
                EXECUTION_ROOT + "/blaze-out/crosstool/testlogs"),
            null,
            RemoteOutputArtifacts.EMPTY);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(OUTPUT_BASE + "/execroot/repo_name/blaze-out/crosstool/bin/com/google/Bla.java");
  }
}
