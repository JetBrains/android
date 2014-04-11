/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.model;

import com.android.tools.idea.designer.AndroidMetaModel;
import com.android.tools.idea.designer.AndroidVariationPaletteItem;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.VariationPaletteItem;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class ViewsMetaManager extends MetaManager {
  public ViewsMetaManager(Project project) {
    super(project, "views-meta-model.xml");
  }

  public static ViewsMetaManager getInstance(Project project) {
    return ServiceManager.getService(project, ViewsMetaManager.class);
  }

  @NotNull
  @Override
  protected AndroidMetaModel createModel(Class<RadComponent> model, String target, String tag) throws Exception {
    return new AndroidMetaModel(model, target, tag);
  }

  @NotNull
  @Override
  protected MetaModel loadModel(ClassLoader classLoader, Element element, Map<MetaModel, List<String>> modelToMorphing) throws Exception {
    AndroidMetaModel meta = (AndroidMetaModel)super.loadModel(classLoader, element, modelToMorphing);
    meta.initializeFrom(element);
    return meta;
  }

  @NotNull
  @Override
  protected VariationPaletteItem createVariationPaletteItem(PaletteItem paletteItem, MetaModel model, Element itemElement) {
    return new AndroidVariationPaletteItem(paletteItem, model, itemElement);
  }
}