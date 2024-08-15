/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.android.tools.rendering;

import com.android.ide.common.rendering.api.Result;
import com.android.tools.idea.rendering.RenderErrorContributor;
import com.android.tools.idea.rendering.RenderErrorContributorImpl;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResults;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.rendering.imagepool.ImagePool.Image;
import com.google.idea.blaze.android.rendering.BlazeRenderErrorContributor;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/** Compat class for {@link RenderResult} */
public final class RenderResultCompat {
  private RenderResult result;

  public RenderResultCompat(RenderResult result) {
    this.result = result;
  }

  public static RenderResultCompat createBlank(PsiFile file) {
    return new RenderResultCompat(RenderResults.createBlank(file));
  }

  public RenderErrorModel createErrorModel() {
    return RenderErrorModelFactory.createErrorModel(null, result);
  }

  public RenderResult get() {
    return result;
  }

  public RenderLogger getLogger() {
    return result.getLogger();
  }

  public Module getModule() {
    return result.getModule();
  }

  public Result getRenderResult() {
    return result.getRenderResult();
  }

  public Image getRenderedImage() {
    return result.getRenderedImage();
  }

  private RenderResultCompat() {}

  /** Extension to provide {@link BlazeRenderErrorContributor}. */
  public static class BlazeProvider extends RenderErrorContributorImpl.Provider {
    @Override
    public boolean isApplicable(Project project) {
      return Blaze.isBlazeProject(project);
    }

    @Override
    public RenderErrorContributor getContributor(
        @Nullable EditorDesignSurface surface, RenderResult result) {
      return new BlazeRenderErrorContributor(surface, result);
    }
  }
}
