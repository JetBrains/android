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
package com.google.idea.blaze.android.targetmaps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import java.util.HashMap;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TargetToBinaryMap} */
@RunWith(JUnit4.class)
public class TargetToBinaryMapTest extends BlazeAndroidIntegrationTestCase {

  private TargetToBinaryMap targetToBinaryMap;

  // Utility map to quickly access Labels in target map. Populated by `buildTargetMap`, and used to
  // ensure the tests don't accidentally access targets not set up in the target map
  private final HashMap<String, Label> targetNameToLabel = new HashMap<>();

  @Before
  public void initTest() {
    TargetMap targetMap = buildTargetMap();
    setTargetMap(targetMap);

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build()));

    setProjectView(
        "targets:",
        "  //com/google/example/simple:bin_a",
        "  //com/google/example/simple:bin_b",
        "  //com/google/example/simple:bin_c");

    targetToBinaryMap = TargetToBinaryMap.getInstance(getProject());
  }

  /**
   * Tests that the source binaries returned by {@link TargetToBinaryMap} match the ones in
   * projectview
   */
  @Test
  public void sourceBinaries_matchesProjectView() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    assertThat(targetToBinaryMap.getSourceBinaryTargets()).containsExactly(binA, binB, binC);
  }

  /**
   * Tests that a binary is considered dependent on itself. A binary target should only have itself
   * as the dependent binary
   */
  @Test
  public void selfBinaryIncluded_singleBinary() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binA)))
        .containsExactly(binA);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binB)))
        .containsExactly(binB);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binC)))
        .containsExactly(binC);
  }

  /**
   * Test that binaries are considered dependent on themselves. A set of binaries should equal the
   * set of binaries that depend on them.
   */
  @Test
  public void selfBinaryIncluded_multipleBinaries() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binA, binB)))
        .containsExactly(binA, binB);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binB, binC)))
        .containsExactly(binB, binC);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binA, binC)))
        .containsExactly(binA, binC);

    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binA, binB, binC)))
        .containsExactly(binA, binB, binC);
  }

  /** Tests that the correct binary is returned for a direct dependency */
  @Test
  public void binaryIncluded_fromUniqueDirectDep() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    TargetKey directDepA = getTargetKey(":dep_a");
    TargetKey directDepB = getTargetKey(":dep_b");
    TargetKey directDepC = getTargetKey(":dep_c");

    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepA)))
        .containsExactly(binA);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepB)))
        .containsExactly(binB);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepC)))
        .containsExactly(binC);
  }

  /**
   * Tests that the correct set of binaries is returned for targets that are shared dependencies of
   * multiple binaries
   */
  @Test
  public void binaryIncluded_fromSharedDirectDep() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    TargetKey directDepAB = getTargetKey(":dep_ab");
    TargetKey directDepBC = getTargetKey(":dep_bc");
    TargetKey directDepAC = getTargetKey(":dep_ac");
    TargetKey directDepABC = getTargetKey(":dep_abc");

    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepAB)))
        .containsExactly(binA, binB);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepBC)))
        .containsExactly(binB, binC);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepAC)))
        .containsExactly(binA, binC);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepABC)))
        .containsExactly(binA, binB, binC);
  }

  /** Tests that the correct binary is returned for a transitive dependency */
  @Test
  public void binaryIncluded_fromDirectTransitiveDep() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    TargetKey transDepA = getTargetKey(":trans_dep_a");
    TargetKey transDepB = getTargetKey(":trans_dep_b");
    TargetKey transDepC = getTargetKey(":trans_dep_c");

    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(transDepA)))
        .containsExactly(binA);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(transDepB)))
        .containsExactly(binB);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(transDepC)))
        .containsExactly(binC);
  }

  /**
   * Tests that the correct set of binaries is returned for transitive dependencies shared by
   * multiple binaries
   */
  @Test
  public void binaryIncluded_fromSharedTransitiveDep() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    TargetKey transDepAB = getTargetKey(":trans_dep_ab");
    TargetKey transDepBC = getTargetKey(":trans_dep_bc");
    TargetKey transDepAC = getTargetKey(":trans_dep_ac");
    TargetKey transDepABC = getTargetKey(":trans_dep_abc");

    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(transDepAB)))
        .containsExactly(binA, binB);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(transDepBC)))
        .containsExactly(binB, binC);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(transDepAC)))
        .containsExactly(binA, binC);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(transDepABC)))
        .containsExactly(binA, binB, binC);
  }

  /**
   * Tests that the correct set of binaries are returned for a mixed set of direct and transitive
   * dependencies
   */
  @Test
  public void binaryIncluded_fromMixedDeps() {
    TargetKey binA = getTargetKey(":bin_a");
    TargetKey binB = getTargetKey(":bin_b");
    TargetKey binC = getTargetKey(":bin_c");

    TargetKey directDepA = getTargetKey(":dep_a");
    TargetKey directDepBC = getTargetKey(":dep_bc");

    TargetKey transDepA = getTargetKey(":trans_dep_a");
    TargetKey transDepAB = getTargetKey(":trans_dep_ab");

    assertThat(
            targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(binA, directDepA, transDepA)))
        .containsExactly(binA);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepA, transDepAB)))
        .containsExactly(binA, binB);
    assertThat(targetToBinaryMap.getBinariesDependingOn(ImmutableList.of(directDepBC, transDepAB)))
        .containsExactly(binA, binB, binC);
  }

  /**
   * Creates a target map with the following dependency structure:
   *
   * <pre>{@code
   * bin_a -> dep_a, dep_ab, dep_ac, dep_abc
   * bin_b -> dep_b, dep_ab, dep_bc, dep_abc
   * bin_c -> dep_c, dep_ac, dep_bc, dep_abc
   *
   * dep_a -> trans_dep_a, trans_dep_ab, trans_dep_ac, trans_dep_abc
   * dep_b -> trans_dep_b, trans_dep_ab, trans_dep_bc, trans_dep_abc
   * dep_c -> trans_dep_c, trans_dep_ac, trans_dep_bc, trans_dep_abc
   *
   * NOTE: xX -> yY means xX is directly dependent on yY
   * }</pre>
   */
  private TargetMap buildTargetMap() {
    Label binA = createAndTrackLabel(":bin_a");
    Label binB = createAndTrackLabel(":bin_b");
    Label binC = createAndTrackLabel(":bin_c");

    Label directDepA = createAndTrackLabel(":dep_a");
    Label directDepB = createAndTrackLabel(":dep_b");
    Label directDepC = createAndTrackLabel(":dep_c");

    Label transDepA = createAndTrackLabel(":trans_dep_a");
    Label transDepB = createAndTrackLabel(":trans_dep_b");
    Label transDepC = createAndTrackLabel(":trans_dep_c");

    Label directDepAB = createAndTrackLabel(":dep_ab");
    Label directDepBC = createAndTrackLabel(":dep_bc");
    Label directDepAC = createAndTrackLabel(":dep_ac");

    Label transDepAB = createAndTrackLabel(":trans_dep_ab");
    Label transDepBC = createAndTrackLabel(":trans_dep_bc");
    Label transDepAC = createAndTrackLabel(":trans_dep_ac");

    Label directDepABC = createAndTrackLabel(":dep_abc");
    Label transDepABC = createAndTrackLabel(":trans_dep_abc");

    return TargetMapBuilder.builder()
        .addTarget(
            mockBinaryTargetIdeInfoBuilder()
                .setLabel(binA)
                .addDependency(directDepA)
                .addDependency(directDepAB)
                .addDependency(directDepAC)
                .addDependency(directDepABC))
        .addTarget(
            mockBinaryTargetIdeInfoBuilder()
                .setLabel(binB)
                .addDependency(directDepB)
                .addDependency(directDepAB)
                .addDependency(directDepBC)
                .addDependency(directDepABC))
        .addTarget(
            mockBinaryTargetIdeInfoBuilder()
                .setLabel(binC)
                .addDependency(directDepC)
                .addDependency(directDepAC)
                .addDependency(directDepBC)
                .addDependency(directDepABC))
        .addTarget(
            mockLibraryTargetIdeInfoBuilder()
                .setLabel(directDepA)
                .addDependency(transDepA)
                .addDependency(transDepAB)
                .addDependency(transDepAC)
                .addDependency(transDepABC))
        .addTarget(
            mockLibraryTargetIdeInfoBuilder()
                .setLabel(directDepB)
                .addDependency(transDepB)
                .addDependency(transDepAB)
                .addDependency(transDepBC)
                .addDependency(transDepABC))
        .addTarget(
            mockLibraryTargetIdeInfoBuilder()
                .setLabel(directDepC)
                .addDependency(transDepC)
                .addDependency(transDepAC)
                .addDependency(transDepBC)
                .addDependency(transDepABC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(directDepAB))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(directDepBC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(directDepAC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepA))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepB))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepAB))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepBC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepAC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepABC))
        .build();
  }

  /**
   * Creates a {@link Label} from the given {@code targetName} and caches it in {@link
   * #targetNameToLabel}
   */
  private Label createAndTrackLabel(String targetName) {
    Label label = Label.create("//com/google/example/simple" + targetName);
    targetNameToLabel.put(targetName, label);
    return label;
  }

  /**
   * Returns the {@link TargetKey} of the Label corresponding to {@code targetName}. This method is
   * used to ensure the tests don't accidentally create and test a label not in target map
   */
  private TargetKey getTargetKey(String targetName) {
    Label label =
        Objects.requireNonNull(
            targetNameToLabel.get(targetName),
            String.format("%s not registered in target map.", targetName));
    return TargetKey.forPlainTarget(label);
  }

  private static TargetIdeInfo.Builder mockLibraryTargetIdeInfoBuilder() {
    return TargetIdeInfo.builder()
        .setKind("android_library")
        .setAndroidInfo(AndroidIdeInfo.builder());
  }

  private static TargetIdeInfo.Builder mockBinaryTargetIdeInfoBuilder() {
    return TargetIdeInfo.builder()
        .setKind("android_binary")
        .setAndroidInfo(AndroidIdeInfo.builder());
  }
}
