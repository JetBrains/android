/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.rendering.tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildMode;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildResult;
import com.google.common.collect.MoreCollectors;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.google3.qsync.InBazelTestProjects;
import com.google.idea.blaze.android.google3.qsync.testrules.BazelTestProjectContext;
import com.google.idea.blaze.android.google3.qsync.testrules.BazelTestProjectContextKt;
import com.google.idea.blaze.android.google3.qsync.testrules.QuerySyncIntegrationTestRule;
import com.google.idea.blaze.android.google3.qsync.testrules.QuerySyncProjectFixtures;
import com.google.idea.blaze.qsync.deps.OutputGroup;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kotlinx.coroutines.guava.ListenableFutureKt;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BazelBuildServicesTest {
  @Rule
  public final QuerySyncIntegrationTestRule rule = new QuerySyncIntegrationTestRule();

  private final BazelBuildServices services = new BazelBuildServices();

  @Test
  public void buildArtifactsAsync() {
    // Arrange
    rule.runTest(rule.prepareTestProject(InBazelTestProjects.SIMPLE_COMPOSE), context -> {
      var tracing = rule.getFixtures().getTracing();

      context.getFixtures().getSyncCompletedListener().invokeAndWaitFor(() -> {
        tracing.clear();
        var reference = getBuildTargetReference(context);

        // Act
        var deferred = services.buildArtifactsAsync(reference);

        return ListenableFutureKt.asListenableFuture(deferred);
      });

      // Assert
      assertTrue(tracing.getCompletedQueries().isEmpty());

      var builds = tracing.getCompletedDependencyBuilds();

      var groups = EnumSet.of(OutputGroup.JARS,
                              OutputGroup.TRANSITIVE_RUNTIME_JARS,
                              OutputGroup.AARS,
                              OutputGroup.GENSRCS,
                              OutputGroup.ARTIFACT_INFO_FILE);

      assertEquals(1, builds.size());
      assertEquals(groups, builds.get(0).getOutputGroups());
    });
  }

  @Test
  public void buildArtifactsAsyncCallsBuildStarted() {
    // Arrange
    rule.runTest(rule.prepareTestProject(InBazelTestProjects.SIMPLE_COMPOSE), context -> {
      var listener = new MockBuildListener();

      context.getFixtures().getSyncCompletedListener().invokeAndWaitFor(() -> {
        services.add(listener);
        var reference = getBuildTargetReference(context);

        // Act
        var deferred = services.buildArtifactsAsync(reference);

        return ListenableFutureKt.asListenableFuture(deferred);
      });

      // Assert
      assertEquals(BuildMode.COMPILE, listener.mode);
      assertEquals(new BuildResult(BuildStatus.SUCCESS, GlobalSearchScope.projectScope(context.getIdeProject())), listener.getResult());
    });
  }

  private static final class MockBuildListener implements BuildListener {
    private BuildMode mode;
    private Future<BuildResult> resultFuture;

    private BuildResult getResult() throws InterruptedException, ExecutionException, TimeoutException {
      return resultFuture.get(100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void buildStarted(@NotNull BuildMode mode, @NotNull ListenableFuture<@NotNull BuildResult> resultFuture) {
      this.mode = mode;
      this.resultFuture = resultFuture;
    }
  }

  private static BazelBuildTargetReference getBuildTargetReference(BazelTestProjectContext<QuerySyncProjectFixtures> context) {
    var file = BazelTestProjectContextKt.virtualFile(context, "main/java/com/basicapp/MainActivity.kt");

    return BuildSystemFilePreviewServicesKt.getBuildTargetReferences(context.getIdeProject(), List.of(file)).stream()
      .map(reference -> (BazelBuildTargetReference)reference)
      .collect(MoreCollectors.onlyElement());
  }
}
