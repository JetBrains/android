/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.decorator;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.naveditor.scene.ThumbnailManager;
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen;
import com.android.tools.idea.rendering.ImagePool;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * {@link SceneDecorator} responsible for creating draw commands for one screen/fragment/destination in the navigation editor.
 */
public class NavScreenDecorator extends SceneDecorator {

  @Override
  protected void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    super.addContent(list, time, sceneContext, component);

    DesignSurface surface = sceneContext.getSurface();
    if (surface == null) {
      return;
    }
    Configuration configuration = surface.getConfiguration();
    AndroidFacet facet = surface.getModel().getFacet();

    String layout = component.getNlComponent().getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT);
    if (layout == null) {
      return;
    }
    ResourceValue value = configuration.getResourceResolver().findResValue(layout, false);
    if (value == null) {
      return;
    }
    String fileName = value.getValue();
    if (fileName == null) {
      return;
    }
    File file = new File(fileName);
    if (!file.exists()) {
      return;
    }
    ThumbnailManager manager = ThumbnailManager.getInstance(facet);
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, false);
    if (virtualFile == null) {
      return;
    }
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(surface.getProject(), virtualFile);
    if (!(psiFile instanceof XmlFile)) {
      return;
    }
    CompletableFuture<ImagePool.Image> thumbnail = manager.getThumbnail((XmlFile)psiFile, surface, configuration);
    if (thumbnail == null) {
      return;
    }
    ImagePool.Image image;
    try {
      // TODO: show progress icon during image creation
      image = thumbnail.get();
    }
    catch (InterruptedException | ExecutionException ignore) {
      // Shouldn't happen
      return;
    }
    list.add(new DrawNavScreen(sceneContext.getSwingX(component.getDrawX()) + 1,
                               sceneContext.getSwingY(component.getDrawY()) + 1,
                               sceneContext.getSwingDimension(component.getDrawWidth()) - 1,
                               sceneContext.getSwingDimension(component.getDrawHeight()) - 1,
                               image));
  }

  @Override
  public void buildList(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    DisplayList displayList = new DisplayList();
    super.buildList(displayList, time, sceneContext, component);
    list.add(NavigationDecorator.createDrawCommand(displayList, component));
  }
}
