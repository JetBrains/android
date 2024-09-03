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
package com.google.idea.blaze.base.filecache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.google.idea.testing.IntellijRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RemoteOutputsCache} */
@RunWith(JUnit4.class)
public class RemoteOutputsCacheTest {
  @Rule public final IntellijRule intellij = new IntellijRule();

  @Before
  public void setUp() throws Exception {
    intellij.registerApplicationService(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void testNormalExtension() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt/foo/bar/SourceFile.java");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("SourceFile_c5ad9a45.java");
  }

  @Test
  public void testNoExtension() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt/include/vector");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("vector_b2da3b80");
  }

  @Test
  public void testDoubleExtension() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt/foo/bar/proto.pb.go");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("proto_5d28a0a7.pb.go");
  }

  @Test
  public void testExtensionOnly() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt/foo/bar/.bazelrc");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("_8f54cd80.bazelrc");
  }

  @Test
  public void testEndingDot() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt/foo/bar/foo.");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_51eabcd4.");
  }

  @Test
  public void testDoubleDot() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt/foo/bar/foo..bar");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_7025f43e..bar");
  }

  @Test
  public void testDotInDirectory() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt/foo.bar/foo.bar");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_509a9fc2.bar");
  }

  @Test
  public void testWindowsStylePath() {
    RemoteOutputArtifact artifact = mock(RemoteOutputArtifact.class);
    when(artifact.getBazelOutRelativePath()).thenReturn("k8-opt\\foo\\bar\\foo.bar");
    assertThat(RemoteOutputsCache.getCacheKey(artifact)).isEqualTo("foo_e9b6ced4.bar");
  }

  @Test
  public void testHashDuplication() {
    RemoteOutputArtifact artifact1 = mock(RemoteOutputArtifact.class);
    RemoteOutputArtifact artifact2 = mock(RemoteOutputArtifact.class);
    // we notice hashCode collision for the following paths. More details can be
    // found in b/249402913#comment16
    when(artifact1.getBazelOutRelativePath())
        .thenReturn(
            "android-armeabi-v7a-fastbuild-ST-537378632435/bin/java/com/google/android/apps/gmm/navigation/ui/common/_migrated/_min_sdk_bumped/common/AndroidManifest.xml");
    when(artifact2.getBazelOutRelativePath())
        .thenReturn(
            "android-armeabi-v7a-fastbuild-ST-537378632435/bin/java/com/google/android/apps/gmm/place/timeline/common/_migrated/_min_sdk_bumped/ImpressionLoggingPropertyNode/AndroidManifest.xml");
    assertThat(RemoteOutputsCache.getCacheKey(artifact1))
        .isNotEqualTo(RemoteOutputsCache.getCacheKey(artifact2));
  }
}
