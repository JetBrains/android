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
package com.google.idea.blaze.base.targetmaps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TransitiveDependencyMap}. */
@RunWith(JUnit4.class)
public class TransitiveDependencyMapTest extends BlazeTestCase {
  private TransitiveDependencyMap transitiveDependencyMap;
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/"));
  private TargetMap targetMap;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    ExtensionPointImpl<Kind.Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
    applicationServices.register(QuerySyncSettings.class, new QuerySyncSettings());

    targetMap = buildTargetMap();
    projectServices.register(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build()));
    projectServices.register(TransitiveDependencyMap.class, new TransitiveDependencyMap(project));
    transitiveDependencyMap = TransitiveDependencyMap.getInstance(project);
  }

  @Test
  public void testStreamIsBreadthFirstTraversal() {
    TargetKey diamondA = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:a"));
    TargetKey diamondB = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:b"));
    TargetKey diamondBB = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:bb"));
    TargetKey diamondBBB =
        TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:bbb"));
    TargetKey diamondC = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:c"));
    TargetKey diamondCC = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:cc"));
    TargetKey diamondCCC =
        TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:ccc"));

    Collection<TargetKey> topLevelTargets = ImmutableSet.of(diamondA, diamondBBB);
    List<TargetKey> deps =
        TransitiveDependencyMap.getTransitiveDependenciesStream(topLevelTargets, targetMap)
            .collect(Collectors.toList());

    assertThat(deps)
        .containsExactly(diamondB, diamondBB, diamondBBB, diamondC, diamondCC, diamondCCC)
        .inOrder();
  }

  @Test
  public void testHasDependencyMatchesGetTransitiveDependencies() {
    Set<TargetKey> keys = targetMap.map().keySet();

    for (TargetKey key : keys) {
      assertThat(
              keys.stream()
                  .filter(it -> transitiveDependencyMap.hasTransitiveDependency(key, it))
                  .collect(Collectors.toSet()))
          .containsExactlyElementsIn(transitiveDependencyMap.getTransitiveDependencies(key));
    }
  }

  @Test
  public void testGetSimpleDependency() {
    TargetKey simpleA = TargetKey.forPlainTarget(Label.create("//com/google/example/simple:a"));
    TargetKey simpleB = TargetKey.forPlainTarget(Label.create("//com/google/example/simple:b"));

    assertThat(transitiveDependencyMap.getTransitiveDependencies(simpleA)).containsExactly(simpleB);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(simpleB)).isEmpty();
  }

  @Test
  public void testGetChainDependencies() {
    TargetKey chainA = TargetKey.forPlainTarget(Label.create("//com/google/example/chain:a"));
    TargetKey chainB = TargetKey.forPlainTarget(Label.create("//com/google/example/chain:b"));
    TargetKey chainC = TargetKey.forPlainTarget(Label.create("//com/google/example/chain:c"));
    TargetKey chainD = TargetKey.forPlainTarget(Label.create("//com/google/example/chain:d"));

    assertThat(transitiveDependencyMap.getTransitiveDependencies(chainA))
        .containsExactly(chainB, chainC, chainD);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(chainB))
        .containsExactly(chainC, chainD);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(chainC)).containsExactly(chainD);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(chainD)).isEmpty();
  }

  @Test
  public void testGetDiamondDependencies() {
    TargetKey diamondA = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:a"));
    TargetKey diamondB = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:b"));
    TargetKey diamondBB = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:bb"));
    TargetKey diamondBBB =
        TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:bbb"));
    TargetKey diamondC = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:c"));
    TargetKey diamondCC = TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:cc"));
    TargetKey diamondCCC =
        TargetKey.forPlainTarget(Label.create("//com/google/example/diamond:ccc"));

    assertThat(transitiveDependencyMap.getTransitiveDependencies(diamondA))
        .containsExactly(diamondB, diamondBB, diamondBBB, diamondC, diamondCC, diamondCCC);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(diamondB))
        .containsExactly(diamondC);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(diamondBB))
        .containsExactly(diamondB, diamondC, diamondCC);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(diamondBBB))
        .containsExactly(diamondC, diamondCC, diamondCCC);
    assertThat(transitiveDependencyMap.getTransitiveDependencies(diamondC)).isEmpty();
    assertThat(transitiveDependencyMap.getTransitiveDependencies(diamondCC)).isEmpty();
    assertThat(transitiveDependencyMap.getTransitiveDependencies(diamondCCC)).isEmpty();
  }

  @Test
  public void testGetDependencyForNonExistentTarget() {
    TargetKey bogus = TargetKey.forPlainTarget(Label.create("//com/google/fake:target"));
    assertThat(transitiveDependencyMap.getTransitiveDependencies(bogus)).isEmpty();
  }

  private static TargetMap buildTargetMap() {
    Label simpleA = Label.create("//com/google/example/simple:a");
    Label simpleB = Label.create("//com/google/example/simple:b");
    Label chainA = Label.create("//com/google/example/chain:a");
    Label chainB = Label.create("//com/google/example/chain:b");
    Label chainC = Label.create("//com/google/example/chain:c");
    Label chainD = Label.create("//com/google/example/chain:d");
    Label diamondA = Label.create("//com/google/example/diamond:a");
    Label diamondB = Label.create("//com/google/example/diamond:b");
    Label diamondBB = Label.create("//com/google/example/diamond:bb");
    Label diamondBBB = Label.create("//com/google/example/diamond:bbb");
    Label diamondC = Label.create("//com/google/example/diamond:c");
    Label diamondCC = Label.create("//com/google/example/diamond:cc");
    Label diamondCCC = Label.create("//com/google/example/diamond:ccc");
    return TargetMapBuilder.builder()
        .addTarget(mockTargetIdeInfoBuilder().setLabel(simpleA).addDependency(simpleB))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(simpleB))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(chainA).addDependency(chainB))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(chainB).addDependency(chainC))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(chainC).addDependency(chainD))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(chainD))
        .addTarget(
            mockTargetIdeInfoBuilder()
                .setLabel(diamondA)
                .addDependency(diamondB)
                .addDependency(diamondBB)
                .addDependency(diamondBBB))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(diamondB).addDependency(diamondC))
        .addTarget(
            mockTargetIdeInfoBuilder()
                .setLabel(diamondBB)
                .addDependency(diamondB)
                .addDependency(diamondC)
                .addDependency(diamondCC))
        .addTarget(
            mockTargetIdeInfoBuilder()
                .setLabel(diamondBBB)
                .addDependency(diamondC)
                .addDependency(diamondCC)
                .addDependency(diamondCCC))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(diamondC))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(diamondCC))
        .addTarget(mockTargetIdeInfoBuilder().setLabel(diamondCCC))
        .build();
  }

  private static TargetIdeInfo.Builder mockTargetIdeInfoBuilder() {
    return TargetIdeInfo.builder().setKind("proto_library");
  }
}
