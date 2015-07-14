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
package com.android.tools.idea.uibuilder.fixtures;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;

import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.createScreen;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ScreenFixture {
  private final NlModel myModel;
  private final SurfaceFixture mySurface;
  private ScreenView myScreen;
  private double myScale = 0.5;
  private int myTranslateX = 0;
  private int myTranslateY = 0;
  private Density myDensity = Density.MEDIUM;

  public ScreenFixture(@NonNull SurfaceFixture surface, @NonNull NlModel model) {
    mySurface = surface;
    myModel = model;
  }

  /**
   * Like {@link #find}, but expects the component to be found
   */
  @NonNull
  public ComponentFixture get(@NonNull String description) {
    ComponentFixture fixture = findById(description);
    if (fixture == null) {
      fixture = findByTag(description);
    }
    assertNotNull(description, fixture);
    return fixture;
  }

  @NonNull
  public ComponentListFixture find(@NonNull String... descriptions) {
    List<ComponentFixture> fixtures = Lists.newArrayList();
    for (String description : descriptions) {
      ComponentFixture fixture = find(description);
      if (fixture != null) {
        fixtures.add(fixture);
      }
    }
    return new ComponentListFixture(this, fixtures);
  }

  @NonNull
  public ComponentListFixture get(@NonNull String... descriptions) {
    List<ComponentFixture> fixtures = Lists.newArrayList();
    for (String description : descriptions) {
      fixtures.add(get(description));
    }
    return new ComponentListFixture(this, fixtures);
  }

  @Nullable
  public ComponentFixture find(@NonNull String description) {
    ComponentFixture fixture = findById(description);
    if (fixture == null) {
      fixture = findByTag(description);
    }
    return fixture;
  }

  @NonNull
  public ComponentFixture getById(@NonNull String id) {
    List<NlComponent> components = findAllById(id);
    ensureAtMostOneMatch("id = " + id, components);
    ensureAtLeastOneMatch("id = " + id, components);
    return new ComponentFixture(this, components.get(0));
  }

  @Nullable
  public ComponentFixture findById(@NonNull String id) {
    List<NlComponent> components = findAllById(id);
    ensureAtMostOneMatch("id = " + id, components);
    return new ComponentFixture(this, components.get(0));
  }

  @NonNull
  public ComponentFixture getByTag(@NonNull String tag) {
    List<NlComponent> components = findAllByTag(tag);
    ensureAtLeastOneMatch("tag = " + tag, components);
    ensureAtMostOneMatch("tag = " + tag, components);
    return new ComponentFixture(this, components.get(0));
  }

  @Nullable
  public ComponentFixture findByTag(@NonNull String tag) {
    List<NlComponent> components = findAllByTag(tag);
    ensureAtMostOneMatch("Tag = " + tag, components);
    return new ComponentFixture(this, components.get(0));
  }

  private void ensureAtMostOneMatch(@NonNull String match, List<NlComponent> components) {
    if (components.size() != 1) {
      fail("Found multiple components with matcher " + match + ": component hierarchy is " + NlComponent.toTree(myModel.getComponents()));
    }
  }

  private void ensureAtLeastOneMatch(@NonNull String match, List<NlComponent> components) {
    if (components.isEmpty()) {
      fail("Could not find component with matcher " + match + ": component hierarchy is " + NlComponent.toTree(myModel.getComponents()));
    }
  }

  @NonNull
  private List<NlComponent> findAllById(@NonNull String id) {
    List<NlComponent> list = Lists.newArrayList();
    for (NlComponent root : myModel.getComponents()) {
      findById(list, root, id);
    }

    return list;
  }

  @NonNull
  private List<NlComponent> findAllByTag(@NonNull String tag) {
    List<NlComponent> list = Lists.newArrayList();
    for (NlComponent root : myModel.getComponents()) {
      findByTag(list, root, tag);
    }

    return list;
  }

  private static void findById(@NonNull List<NlComponent> result, @NonNull NlComponent component, @NonNull String id) {
    if (id.equals(component.getAttribute(ANDROID_URI, ATTR_ID))) {
      result.add(component);
    }

    for (NlComponent child : component.getChildren()) {
      findById(result, child, id);
    }
  }

  @NonNull
  private static List<NlComponent> findByTag(@NonNull NlComponent component, @NonNull String tag) {
    List<NlComponent> list = Lists.newArrayList();
    findByTag(list, component, tag);
    return list;
  }

  private static void findByTag(@NonNull List<NlComponent> result, @NonNull NlComponent component, @NonNull String tag) {
    if (tag.equals(component.getTagName())) {
      result.add(component);
    }

    for (NlComponent child : component.getChildren()) {
      findByTag(result, child, tag);
    }
  }

  @NonNull
  public ScreenFixture withScale(double scale) {
    myScale = scale;
    return this;
  }

  @NonNull
  public ScreenFixture withDensity(@NonNull Density density) {
    myDensity = density;
    return this;
  }

  @NonNull
  public ScreenFixture withOffset(@SwingCoordinate int x, @SwingCoordinate int y) {
    myTranslateX = x;
    myTranslateY = y;
    return this;
  }

  @NonNull
  public ScreenView getScreen() {
    if (myScreen == null) {
      myScreen = createScreen(mySurface.getSurface(), myModel, new SelectionModel(), myScale, myTranslateX, myTranslateY, myDensity);
    }
    return myScreen;
  }
}
