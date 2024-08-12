/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.devtools.intellij.aspect.Common.ArtifactLocation;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link ArtifactLocationParser}. */
@RunWith(JUnit4.class)
public class ArtifactLocationParserTest {

  @Test
  public void testConverterSourceArtifact() {
    ArtifactLocation parsed =
        ArtifactLocationParser.parse(Joiner.on(',').join("", "test.java", "0"));
    assertThat(parsed)
        .isEqualTo(
            ArtifactLocation.newBuilder()
                .setRelativePath(Paths.get("test.java").toString())
                .setIsSource(true)
                .setIsExternal(false)
                .build());
  }

  @Test
  public void testConverterDerivedArtifact() {
    ArtifactLocation parsed =
        ArtifactLocationParser.parse(Joiner.on(',').join("bin", "java/com/test.java", "0"));
    assertThat(parsed)
        .isEqualTo(
            ArtifactLocation.newBuilder()
                .setRootExecutionPathFragment(Paths.get("bin").toString())
                .setRelativePath(Paths.get("java/com/test.java").toString())
                .setIsSource(false)
                .setIsExternal(false)
                .build());
  }

  @Test
  public void testConverterExternal() {
    ArtifactLocation externalArtifact =
        ArtifactLocationParser.parse(Joiner.on(',').join("", "test.java", "1"));
    assertThat(externalArtifact)
        .isEqualTo(
            ArtifactLocation.newBuilder()
                .setRelativePath(Paths.get("test.java").toString())
                .setIsSource(true)
                .setIsExternal(true)
                .build());
    ArtifactLocation nonExternalArtifact =
        ArtifactLocationParser.parse(Joiner.on(',').join("", "test.java", "0"));
    assertThat(nonExternalArtifact)
        .isEqualTo(
            ArtifactLocation.newBuilder()
                .setRelativePath(Paths.get("test.java").toString())
                .setIsSource(true)
                .setIsExternal(false)
                .build());
  }

  @Test
  public void testInvalidFormatFails() {
    assertFails("/root", ArtifactLocationParser.INVALID_FORMAT);
    assertFails("/root,exec,rel,extra", ArtifactLocationParser.INVALID_FORMAT);
  }

  @Test
  public void testParsingArtifactLocationList() {
    String input =
        Joiner.on(':')
            .join(
                Joiner.on(',').join("", "test.java", "0"),
                Joiner.on(',').join("bin", "java/com/test.java", "0"),
                Joiner.on(',').join("", "test.java", "1"));

    assertThat(ArtifactLocationParser.parseList(input))
        .containsExactly(
            ArtifactLocation.newBuilder()
                .setRelativePath(Paths.get("test.java").toString())
                .setIsSource(true)
                .build(),
            ArtifactLocation.newBuilder()
                .setRootExecutionPathFragment(Paths.get("bin").toString())
                .setRelativePath(Paths.get("java/com/test.java").toString())
                .setIsSource(false)
                .build(),
            ArtifactLocation.newBuilder()
                .setRelativePath(Paths.get("test.java").toString())
                .setIsSource(true)
                .setIsExternal(true)
                .build());
  }

  private void assertFails(String input, String expectedError) {
    try {
      ArtifactLocationParser.parse(input);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo(expectedError);
    }
  }
}
