/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the {@link ViewEditor} abstraction presented
 * to {@link ViewHandler} instances
 */
public class ViewEditorImpl extends ViewEditor {
  private final ScreenView myScreen;

  public ViewEditorImpl(@NotNull ScreenView screen) {
    myScreen = screen;
  }

  @Override
  public int getDpi() {
    return myScreen.getConfiguration().getDensity().getDpiValue();
  }

  @Nullable
  @Override
  public AndroidVersion getCompileSdkVersion() {
    return AndroidModuleInfo.get(myScreen.getModel().getFacet()).getBuildSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    return AndroidModuleInfo.get(myScreen.getModel().getFacet()).getMinSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return AndroidModuleInfo.get(myScreen.getModel().getFacet()).getTargetSdkVersion();
  }

  @NotNull
  @Override
  public Configuration getConfiguration() {
    return myScreen.getConfiguration();
  }

  @NotNull
  @Override
  public NlModel getModel() {
    return myScreen.getModel();
  }

  @Nullable
  @Override
  public Map<NlComponent, Dimension> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter) {
    // TODO: Reuse snapshot!
    Map<NlComponent, Dimension> unweightedSizes = Maps.newHashMap();
    XmlTag parentTag = parent.getTag();
    if (parentTag.isValid()) {
      if (parent.getChildCount() == 0) {
        return Collections.emptyMap();
      }
      Map<XmlTag, NlComponent> tagToComponent = Maps.newHashMapWithExpectedSize(parent.getChildCount());
      for (NlComponent child : parent.getChildren()) {
        tagToComponent.put(child.getTag(), child);
      }

      NlModel model = myScreen.getModel();
      XmlFile xmlFile = model.getFile();
      AndroidFacet facet = model.getFacet();
      RenderService renderService = RenderService.get(facet);
      RenderLogger logger = renderService.createLogger();
      final RenderTask task = renderService.createTask(xmlFile, getConfiguration(), logger, null);
      if (task == null) {
        return null;
      }

      // Measure unweighted bounds
      Map<XmlTag, ViewInfo> map = task.measureChildren(parentTag, filter);
      task.dispose();
      if (map != null) {
        for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
          ViewInfo viewInfo = entry.getValue();
          viewInfo = RenderService.getSafeBounds(viewInfo);
          Dimension size = new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
          NlComponent child = tagToComponent.get(entry.getKey());
          if (child != null) {
            unweightedSizes.put(child, size);
          }
        }
      }
    }

    return unweightedSizes;
  }

  @Nullable
  @Override
  public String displayResourceInput(@NotNull String title, @NotNull Collection<ResourceType> types) {
    ChooseResourceDialog dialog = new ChooseResourceDialog(myScreen.getModel().getModule(), types.toArray(new ResourceType[0]), null, null);

    if (!title.isEmpty()) {
      dialog.setTitle(title);
    }

    dialog.show();

    if (dialog.isOK()) {
      String resource = dialog.getResourceName();

      if (resource != null && !resource.isEmpty()) {
        return resource;
      }
    }

    return null;
  }

  @Nullable
  @Override
  public String displayClassInput(@NotNull Set<String> superTypes, @Nullable String currentValue) {
    Module module = myScreen.getModel().getModule();
    String[] superTypesArray = ArrayUtil.toStringArray(superTypes);

    return ChooseClassDialog.openDialog(module, "Classes", true, superTypesArray);
  }

  @NotNull
  public ScreenView getScreenView() {
    return myScreen;
  }
}
