/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder;

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.uibuilder.model.NlComponentHelper;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.Lists;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for creating a {@link NlModel} for a test.
 */
public class NlModelBuilderUtil {

  // Prevent instantiation...
  private NlModelBuilderUtil() {}

  @NotNull
  public static ModelBuilder model(@NotNull AndroidProjectRule rule,
                                   @NotNull String resourceFolder,
                                   @NotNull String name,
                                   @NotNull ComponentDescriptor root) {
    return model(AndroidFacet.getInstance(rule.module), (JavaCodeInsightTestFixture)rule.fixture, resourceFolder, name, root);
  }

  @NotNull
  public static ModelBuilder model(@NotNull AndroidFacet facet,
                                   @NotNull JavaCodeInsightTestFixture fixture,
                                   @NotNull String resourceFolder,
                                   @NotNull String name,
                                   @NotNull ComponentDescriptor root) {
    return new ModelBuilder(
      facet,
      fixture,
      name,
      root,
      (@NotNull SyncNlModel model) -> {
        LayoutlibSceneManager.updateHierarchy(buildViewInfos(model, root), model);
        return new SyncLayoutlibSceneManager(model);
      },
      (@NotNull NlModel model, @NotNull NlModel newModel) -> LayoutlibSceneManager.updateHierarchy(
        AndroidPsiUtils.getRootTagSafely(newModel.getFile()), buildViewInfos(newModel, root), model),
      resourceFolder,
      NlDesignSurface.class,
      (@NotNull NlComponent component) -> NlComponentHelper.INSTANCE.registerComponent(component));
  }

  @NotNull
  private static List<ViewInfo> buildViewInfos(@NotNull NlModel model, @NotNull ComponentDescriptor root) {
    List<ViewInfo> infos = Lists.newArrayList();
    XmlFile file = model.getFile();
    assertThat(file).isNotNull();
    assertThat(file.getRootTag()).isNotNull();
    infos.add(root.createViewInfo(null, file.getRootTag()));
    return infos;
  }
}
