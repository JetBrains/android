// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.sync.idea;

import static com.android.tools.idea.gradle.project.sync.GradleSyncStateImplKt.getProjectSyncRequest;
import static com.android.tools.idea.projectsystem.gradle.sync.AndroidModuleDataServiceKt.recommendsPluginUpgradeAfterImport;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import org.junit.After;
import org.junit.Test;

public class VariantSwitcherSyncRequestTest {
  private static final String ROOT_PATH = "/project";

  private final Disposable myDisposable = Disposer.newDisposable();
  private final Project myProject = new MockProject(null, myDisposable);

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
  }

  @Test
  public void importOfCachedVariantInIntelliJDoesNotRecommendPluginUpgrade() {
    List<Boolean> recommendationsDuringImport = new ArrayList<>();

    VariantSwitcher.importAsVariantSwitch(myProject, ROOT_PATH, () -> {
      GradleSyncInvoker.Request request = getProjectSyncRequest(myProject, ROOT_PATH);
      recommendationsDuringImport.add(recommendsPluginUpgradeAfterImport(request == null ? null : request.getTrigger(), false));
      return Unit.INSTANCE;
    });

    assertThat(recommendationsDuringImport).containsExactly(false);
  }

  @Test
  public void importOfCachedVariantLeavesNoSyncRequestBehind() {
    try {
      VariantSwitcher.importAsVariantSwitch(myProject, ROOT_PATH, () -> {
        throw new IllegalStateException("import failed");
      });
    }
    catch (IllegalStateException ignored) {
    }

    assertThat(getProjectSyncRequest(myProject, ROOT_PATH)).isNull();
  }
}
