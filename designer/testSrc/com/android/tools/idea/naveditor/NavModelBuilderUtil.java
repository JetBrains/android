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
package com.android.tools.idea.naveditor;

import com.android.SdkConstants;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Function;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.ATTR_GRAPH;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_INCLUDE;
import static org.jetbrains.android.dom.navigation.NavigationSchema.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Descriptors used for building navigation {@link com.android.tools.idea.common.model.NlModel}s
 */
public class NavModelBuilderUtil {
  private static final String TAG_FRAGMENT = "fragment";
  private static final String TAG_NAVIGATION = "navigation";

  @NotNull
  public static ModelBuilder model(@NotNull String name, @NotNull ComponentDescriptor root, @NotNull AndroidFacet facet,
                                   @NotNull JavaCodeInsightTestFixture fixture) {
    Function<? super SyncNlModel, ? extends SceneManager> managerFactory = model -> {
      NavDesignSurface surface = (NavDesignSurface)model.getSurface();

      when(surface.getSchema()).thenReturn(NavigationSchema.getOrCreateSchema(facet));
      when(surface.getCurrentNavigation()).then(invocation -> model.getComponents().get(0));
      when(surface.getExtentSize()).thenReturn(new Dimension(500, 500));
      when(surface.getScrollPosition()).thenReturn(new Point(0, 0));

      SelectionModel selectionModel = mock(SelectionModel.class);
      when(selectionModel.getSelection()).thenReturn(ImmutableList.of());

      SceneView sceneView = mock(SceneView.class);
      when(sceneView.getModel()).thenReturn(model);
      when(sceneView.getConfiguration()).thenReturn(model.getConfiguration());
      when(sceneView.getSelectionModel()).thenReturn(selectionModel);

      when(surface.getCurrentSceneView()).thenReturn(sceneView);

      return new NavSceneManager(model, surface);
    };

    return new ModelBuilder(facet, fixture, name, root, managerFactory,
                            NavSceneManager::updateHierarchy, "navigation", NavDesignSurface.class);
  }

  @NotNull
  public static NavigationComponentDescriptor rootComponent(@Nullable String id) {
    NavigationComponentDescriptor descriptor = new NavigationComponentDescriptor();
    if (id != null) {
      descriptor.id("@+id/" + id);
    }
    return descriptor;
  }

  @NotNull
  public static NavigationComponentDescriptor navigationComponent(@Nullable String id) {
    NavigationComponentDescriptor descriptor = new NavigationComponentDescriptor();
    if (id != null) {
      descriptor.id("@+id/" + id);
    }
    return descriptor;
  }

  @NotNull
  public static IncludeComponentDescriptor includeComponent(@NotNull String graphId) {
    IncludeComponentDescriptor descriptor = new IncludeComponentDescriptor();
    descriptor.withAttribute(AUTO_URI, ATTR_GRAPH, NAVIGATION_PREFIX + graphId);
    return descriptor;
  }

  @NotNull
  public static FragmentComponentDescriptor fragmentComponent(@NotNull String id) {
    FragmentComponentDescriptor descriptor = new FragmentComponentDescriptor();
    descriptor.id("@+id/" + id);
    return descriptor;
  }

  @NotNull
  public static ActionComponentDescriptor actionComponent(@NotNull String id) {
    ActionComponentDescriptor descriptor = new ActionComponentDescriptor();
    descriptor.id("@+id/" + id);
    return descriptor;
  }

  @NotNull
  public static ActivityComponentDescriptor activityComponent(@NotNull String id) {
    ActivityComponentDescriptor descriptor = new ActivityComponentDescriptor();
    descriptor.id("@+id/" + id);
    return descriptor;
  }

  @NotNull
  public static DeepLinkComponentDescriptor deepLinkComponent(@NotNull String uri) {
    DeepLinkComponentDescriptor descriptor = new DeepLinkComponentDescriptor();
    descriptor.withUriAttribute(uri);
    return descriptor;
  }

  @NotNull
  public static ArgumentComponentDescriptor argumentComponent(@NotNull String name) {
    return new ArgumentComponentDescriptor(name);
  }

  public static class NavigationComponentDescriptor extends ComponentDescriptor {
    public NavigationComponentDescriptor() {
      super(TAG_NAVIGATION);
    }

    @NotNull
    public NavigationComponentDescriptor withStartDestinationAttribute(@NotNull String startDestination) {
      withAttribute(AUTO_URI, ATTR_START_DESTINATION, "@id/" + startDestination);
      return this;
    }

    @NotNull
    public NavigationComponentDescriptor withLabelAttribute(@NotNull String label) {
      withAttribute(ANDROID_URI, ATTR_LABEL, label);
      return this;
    }
  }

  public static class FragmentComponentDescriptor extends ComponentDescriptor {
    public FragmentComponentDescriptor() {
      super(TAG_FRAGMENT);
    }

    @NotNull
    public FragmentComponentDescriptor withLayoutAttribute(@NotNull String layout) {
      withAttribute(TOOLS_URI, ATTR_LAYOUT, "@layout/" + layout);
      return this;
    }
  }

  public static class ActionComponentDescriptor extends ComponentDescriptor {
    public ActionComponentDescriptor() {
      super(TAG_ACTION);
    }

    @NotNull
    public ActionComponentDescriptor withDestinationAttribute(@NotNull String destination) {
      withAttribute(AUTO_URI, ATTR_DESTINATION, "@id/" + destination);
      return this;
    }
  }

  public static class ActivityComponentDescriptor extends ComponentDescriptor {
    public ActivityComponentDescriptor() {
      super(TAG_ACTIVITY);
    }
  }

  public static class IncludeComponentDescriptor extends ComponentDescriptor {
    public IncludeComponentDescriptor() {
      super(TAG_INCLUDE);
    }
  }

  public static class DeepLinkComponentDescriptor extends ComponentDescriptor {
    public DeepLinkComponentDescriptor() {
      super(TAG_DEEPLINK);
    }

    @NotNull
    public DeepLinkComponentDescriptor withUriAttribute(@NotNull String uri) {
      withAttribute(AUTO_URI, "uri", uri);
      return this;
    }
  }

  public static class ArgumentComponentDescriptor extends ComponentDescriptor {
    public ArgumentComponentDescriptor(@NotNull String name) {
      super(TAG_ARGUMENT);
      withAttribute(SdkConstants.ATTR_NAME, name);
    }

    public ArgumentComponentDescriptor withDefaultValueAttribute(@NotNull String defaultValue) {
      withAttribute(NavigationSchema.ATTR_DEFAULT_VALUE, defaultValue);
      return this;
    }
  }
}
