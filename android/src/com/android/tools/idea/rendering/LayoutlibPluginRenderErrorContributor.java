/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.utils.HtmlBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LayoutlibPluginRenderErrorContributor extends RenderErrorContributor {
  private final HtmlLinkManager myLinkManager;

  protected LayoutlibPluginRenderErrorContributor(@Nullable EditorDesignSurface surface,
                                                  @NotNull RenderResult result,
                                                  @Nullable DataContext dataContext) {
    super(surface, result, dataContext);
    myLinkManager = result.getLogger().getLinkManager();
  }

  @Override
  public Collection<RenderErrorModel.Issue> reportIssues() {
    RenderResult result = getResult();
    RenderLogger logger = result.getLogger();
    if (!LayoutLibrary.isNative()) {
      reportLayoutlibStandardIssue();
    } else if (logger.hasErrors()) {
      reportLayoutlibNativeIssue();
    }
    return getIssues();
  }

  private void reportLayoutlibStandardIssue() {
    HtmlBuilder builder = new HtmlBuilder();
    builder
      .addLink("Click ", "here", " to enable the new Layout Rendering Engine.",
                     myLinkManager.createEnableLayoutlibNativeUrl())
      .newline()
      .add("If it causes any issue, it can later be disabled using the Settings > Experimental dialog.");
    addIssue()
      .setSeverity(HighlightSeverity.INFORMATION)
      .setSummary("Use new Layout Rendering Engine")
      .setHtmlContent(builder)
      .build();
  }

  private void reportLayoutlibNativeIssue() {
    HtmlBuilder builder = new HtmlBuilder();
    builder
      .add("Rendering errors might be caused by the new Layout Rendering Engine.")
      .newline()
      .addLink("Disabling it by clicking ", "here", " may fix the issue.",
               myLinkManager.createDisableLayoutlibNativeUrl())
      .newline()
      .add("It can later be enabled again using the Settings > Experimental dialog.");
    addIssue()
      .setSeverity(HighlightSeverity.INFORMATION)
      .setSummary("Disable experimental Layout Rendering Engine.")
      .setHtmlContent(builder)
      .build();
  }

  public static class LayoutlibPluginProvider extends Provider {
    @Override
    public RenderErrorContributor getContributor(@Nullable EditorDesignSurface surface,
                                                 @NotNull RenderResult result,
                                                 @Nullable DataContext dataContext) {
      return new LayoutlibPluginRenderErrorContributor(surface, result, dataContext);
    }
  }
}
