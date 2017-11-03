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
package com.android.tools.idea.common.fixtures;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.fixtures.DragFixture;
import com.android.tools.idea.uibuilder.fixtures.ResizeFixture;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.common.util.NlTreeDumper;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ComponentFixture {
  private final ScreenFixture myScreenFixture;
  private final NlComponent myComponent;

  public ComponentFixture(@NotNull ScreenFixture screenFixture, @NotNull NlComponent component) {
    myScreenFixture = screenFixture;
    myComponent = component;
  }

  @NotNull
  public ComponentListFixture singleton() {
    return new ComponentListFixture(myScreenFixture, Collections.singletonList(this));
  }

  public ResizeFixture resize(@NotNull SegmentType edge1, @NotNull SegmentType edge2) {
    return new ResizeFixture(this,
                             edge1.isHorizontal() ? edge1 : edge2,
                             edge1.isHorizontal() ? edge2 : edge1);
  }

  public ResizeFixture resize(@NotNull SegmentType edge) {
    return new ResizeFixture(this,
                             edge.isHorizontal() ? edge : null,
                             edge.isHorizontal() ? null : edge);
  }

  public DragFixture drag() {
    return new DragFixture(singleton());
  }

  public ScrollFixture scroll() { return new ScrollFixture(myScreenFixture, this); }

  public ComponentFixture expectWidth(@NotNull String width) {
    assertEquals("Wrong width", width, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), ANDROID_URI, ATTR_LAYOUT_WIDTH));
    return this;
  }

  public ComponentFixture expectHeight(@NotNull String height) {
    assertEquals("Wrong height", height, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), ANDROID_URI, ATTR_LAYOUT_HEIGHT));
    return this;
  }

  public ComponentFixture expectAttribute(@NotNull String name, @NotNull String value) {
    assertEquals("Wrong " + name, value, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), ANDROID_URI, name));
    return this;
  }

  public ComponentFixture expectAttribute(@NotNull String namespace, @NotNull String name, @NotNull String value) {
    assertEquals("Wrong " + name, value, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), namespace, name));
    return this;
  }

  @NotNull
  public ComponentFixture expectXml(@NotNull @Language("XML") String xml) {
    assertEquals(xml, myComponent.getTag().getText());
    return this;
  }

  @NotNull
  public ScreenView getScreen() {
    return myScreenFixture.getScreen();
  }

  @NotNull
  public NlComponent getComponent() {
    return myComponent;
  }

  @Nullable
  public SceneComponent getSceneComponent() {
    return myScreenFixture.getScreen().getScene().getSceneComponent(myComponent);
  }

  @NotNull
  public ComponentFixture parent() {
    assertNotNull(myComponent.getParent());
    return new ComponentFixture(myScreenFixture, myComponent.getParent());
  }

  @NotNull
  public ComponentFixture expectHierarchy(@NotNull String hierarchy) {
    String tree = NlTreeDumper.dumpTree(Collections.singletonList(myComponent));
    assertEquals(tree, hierarchy);
    return this;
  }
}
