// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.util;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class EmbeddedDistributionPathsTest extends TestCase {

  private final String PATH_SEPARATOR = System.getProperty("path.separator");
  private EmbeddedDistributionPaths inst = new EmbeddedDistributionPaths();

  public void testRepoPathFromEmptyString() {
    List<File> res = inst.repoPathsFromString("", f -> true);
    assertPathsEqual(res, "");
  }

  public void testRepoPathFromString() {
    List<File> res = inst.repoPathsFromString("/somewhere", f -> true);
    assertPathsEqual(res, "/somewhere");
  }

  public void testTwoRepoPathsFromString() {
    List<File> res = inst.repoPathsFromString("/somewhere" + PATH_SEPARATOR + "/somewhereelse", f -> true);
    assertPathsEqual(res, "/somewhere", "/somewhereelse");
  }

  @Test
  public void testFindAndroidStudioLocalMavenRepoPaths() {
    File rootDir = new File(StudioPathManager.getSourcesRoot());
    List<File> expectedRepo = Arrays.asList(new File(rootDir, "out/repo"),
                                            new File(rootDir, "out/studio/repo"),
                                            new File(rootDir, "prebuilts/tools/common/m2/repository"));
    expectedRepo = expectedRepo.stream().filter(File::isDirectory).collect(Collectors.toList());
    // Invoke the method to test.
    List<File> paths = inst.doFindAndroidStudioLocalMavenRepoPaths();
    assertThat(paths).hasSize(expectedRepo.size());
    assertThat(paths).containsExactlyElementsIn(expectedRepo);
  }

  private static void assertPathsEqual(List<File> actual, String... expected) {
    Assert.assertEquals(new ArrayList<>(Arrays.asList(expected)), new ArrayList<>(ContainerUtil.map(actual, File::toString)));
  }
}