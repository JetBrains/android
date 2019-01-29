/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.rendering;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.rendering.RenderErrorContributor;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.utils.HtmlBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.Map;

public class GradleRenderErrorContributor extends RenderErrorContributor {
  public GradleRenderErrorContributor(@Nullable EditorDesignSurface surface, @NotNull RenderResult result, @Nullable DataContext dataContext) {
    super(surface, result, dataContext);
  }

  @Override
  public Collection<RenderErrorModel.Issue> reportIssues() {
    RenderResult result = getResult();
    RenderLogger logger = result.getLogger();
    AndroidFacet facet = AndroidFacet.getInstance(result.getModule());
    reportIssue170841(logger, facet);
    return getIssues();
  }

  private void reportIssue170841(RenderLogger logger, AndroidFacet facet) {
    Map<String, Throwable> brokenClasses = logger.getBrokenClasses();
    if (brokenClasses.isEmpty() || facet == null) {
      return;
    }
    AndroidModuleModel model = AndroidModuleModel.get(facet);
    if (model == null || !model.getFeatures().isLayoutRenderingIssuePresent()) {
      return;
    }

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        FixAndroidGradlePluginVersionHyperlink
          quickFix = new FixAndroidGradlePluginVersionHyperlink(GradleVersion.parse(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion()), null);
        quickFix.executeIfClicked(facet.getModule().getProject(),
                                  new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, quickFix.getUrl()));
      }
    };
    HtmlBuilder builder = new HtmlBuilder();
    HtmlLinkManager linkManager = logger.getLinkManager();
    builder.add("Tip: Either ")
      .addLink("update the Gradle plugin build version to 1.2.3", linkManager.createRunnableLink(runnable))
      .add(" or later, or downgrade to version 1.1.3, or as a workaround, ");
    builder.beginList()
      .listItem().addLink("", "Build the project", ", then", linkManager.createBuildProjectUrl())
      .listItem().addLink("", "Gradle Sync the project", ", then", linkManager.createSyncProjectUrl())
      .listItem().addLink("Manually ", "refresh the layout", " (or restart the IDE)", linkManager.createRefreshRenderUrl())
      .endList();
    builder.newline();

    GradleVersion modelVersion = model.getModelVersion();
    addIssue()
      .setSeverity(HighlightSeverity.ERROR)
      .setSummary("Using an obsolete version of the Gradle plugin (" + modelVersion + "); "
                  + "this can lead to layouts not rendering correctly.")
      .setHtmlContent(builder)
      .build();
  }

  public static class GradleProvider extends Provider {
    @Override
    public boolean isApplicable(Project project) {
      return GradleProjectInfo.getInstance(project).isBuildWithGradle();
    }

    @Override
    public RenderErrorContributor getContributor(@Nullable EditorDesignSurface surface,
                                                 @NotNull RenderResult result,
                                                 @Nullable DataContext dataContext) {
      return new GradleRenderErrorContributor(surface, result, dataContext);
    }
  }
}
