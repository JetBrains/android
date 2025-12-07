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
package com.google.idea.blaze.android.rendering;

import com.android.tools.idea.rendering.RenderErrorContributor;
import com.android.tools.idea.rendering.RenderUtils;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.rendering.HtmlLinkManager;
import com.android.tools.rendering.RenderLogger;
import com.android.tools.rendering.RenderResult;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Contribute blaze specific render errors. */
public class BlazeRenderErrorContributor implements RenderErrorContributor {
  private final EditorDesignSurface designSurface;
  private final RenderLogger logger;
  private final Module module;
  private final PsiFile sourceFile;
  private final Project project;
  private final HtmlLinkManager linkManager;
  private final HyperlinkListener linkHandler;
  private final Set<RenderErrorModel.Issue> issues = new LinkedHashSet<>();

  public BlazeRenderErrorContributor(EditorDesignSurface surface, RenderResult result) {
    designSurface = surface;
    logger = result.getLogger();
    module = result.getModule();
    sourceFile = result.getSourceFile();
    project = module.getProject();
    linkManager = logger.getLinkManager();
    linkHandler =
        e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            JEditorPane pane = (JEditorPane) e.getSource();
            if (e instanceof HTMLFrameHyperlinkEvent) {
              HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent) e;
              HTMLDocument doc = (HTMLDocument) pane.getDocument();
              doc.processHTMLFrameHyperlinkEvent(evt);
              return;
            }

            performClick(e.getDescription());
          }
        };
  }

  private Collection<RenderErrorModel.Issue> getIssues() {
    return Collections.unmodifiableCollection(issues);
  }

  private void performClick(String url) {
    linkManager.handleUrl(
        url,
        module,
        sourceFile,
        true,
        new HtmlLinkManager.RefreshableSurface() {
          @Override
          public void handleRefreshRenderUrl() {
            if (designSurface != null) {
              // TODO(b/321801969): Remove and replace with direct call when in repo.
              // Use reflection to getConfigurations() from designSurface. Can't call directly
              // because it returns an incompatible version of ImmutableCollection.
              // RenderUtils.clearCache(designSurface.getConfigurations()); would fail at runtime.
              try {
                Method getConfigurationsMethod =
                    EditorDesignSurface.class.getMethod("getConfigurations", null);
                Object configurations = getConfigurationsMethod.invoke(designSurface);
                Method clearCacheMethod =
                    RenderUtils.class.getMethod(
                        "clearCache", getConfigurationsMethod.getReturnType());
                clearCacheMethod.invoke(null, configurations);
              } catch (NoSuchMethodException ex) {
                throw new RuntimeException(
                    "Error using reflection to get getConfigurations() instance method: " + ex);
              } catch (IllegalAccessException ex) {
                throw new RuntimeException(
                    "Error accessing getConfigurations() instance method" + ex);
              } catch (InvocationTargetException ex) {
                throw new RuntimeException("Error invoking target getConfigurations(): " + ex);
              }
              designSurface.forceUserRequestedRefresh();
            }
          }

          @Override
          public void requestRender() {
            if (designSurface != null) {
              designSurface.forceUserRequestedRefresh();
            }
          }
        });
  }

  @Override
  public Collection<RenderErrorModel.Issue> reportIssues() {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null || !logger.hasErrors()) {
      return getIssues();
    }

    // TODO(b/284002829): Setup resource-module specific issue reporting
    return getIssues();
  }

  public static class Provider implements RenderErrorContributor.Provider {

    public boolean isApplicable(Project project) {
      return Blaze.isBlazeProject(project)
             && Blaze.getBuildSystemName(project) == BuildSystemName.Blaze;
    }

    public RenderErrorContributor getContributor(@Nullable EditorDesignSurface surface, @NotNull RenderResult result) {
      return new BlazeRenderErrorContributor(surface, result);
    }
  }
}
