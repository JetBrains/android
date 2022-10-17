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
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
    return model(AndroidFacet.getInstance(rule.getModule()), rule.fixture, resourceFolder, name, root);
  }

  @NotNull
  public static ModelBuilder model(@NotNull AndroidFacet facet,
                                   @NotNull CodeInsightTestFixture fixture,
                                   @NotNull String resourceFolder,
                                   @NotNull String name,
                                   @NotNull ComponentDescriptor root) {
    return new ModelBuilder(
      facet,
      fixture,
      name,
      root,
      (@NotNull DesignSurface<? extends SceneManager> surface, @NotNull SyncNlModel model) -> {
        NlModelHierarchyUpdater.updateHierarchy(buildViewInfos(model, root), model);
        return new SyncLayoutlibSceneManager((DesignSurface<LayoutlibSceneManager>)surface, model);
      },
      (@NotNull NlModel model) -> NlModelHierarchyUpdater.updateHierarchy(buildViewInfos(model, root), model),
      resourceFolder,
      NlDesignSurface.class,
      NlInteractionHandler::new,
      (@NotNull NlComponent component) -> NlComponentRegistrar.INSTANCE.accept(component));
  }

  @NotNull
  private static List<ViewInfo> buildViewInfos(@NotNull NlModel model, @NotNull ComponentDescriptor root) {
    List<ViewInfo> infos = new ArrayList<>();
    XmlFile file = model.getFile();
    assertThat(file).isNotNull();
    assertThat(file.getRootTag()).isNotNull();
    infos.add(root.createViewInfo(null, file.getRootTag()));
    return infos;
  }

  @NotNull
  public static List<SyncLayoutlibSceneManager> getSyncLayoutlibSceneManagersForModel(@NotNull SyncNlModel model) {
    return model.getSurface().getSceneManagers().stream()
      .map(SyncLayoutlibSceneManager.class::cast)
      .collect(Collectors.toList());
  }

  @NotNull
  public static SyncLayoutlibSceneManager getSyncLayoutlibSceneManagerForModel(@NotNull SyncNlModel model) {
    return getSyncLayoutlibSceneManagersForModel(model).get(0);
  }
}
