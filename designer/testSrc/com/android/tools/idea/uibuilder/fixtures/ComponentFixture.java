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
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.intellij.lang.annotations.Language;

import java.util.Collections;

import static com.android.SdkConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ComponentFixture {
  private final ScreenFixture myScreenFixture;
  private final NlComponent myComponent;

  public ComponentFixture(@NonNull ScreenFixture screenFixture, @NonNull NlComponent component) {
    myScreenFixture = screenFixture;
    myComponent = component;
  }

  @NonNull
  public ComponentListFixture singleton() {
    return new ComponentListFixture(myScreenFixture, Collections.singletonList(this));
  }

  public ResizeFixture resize(@NonNull SegmentType edge1, @NonNull SegmentType edge2) {
    return new ResizeFixture(this,
                             edge1.isHorizontal() ? edge1 : edge2,
                             edge1.isHorizontal() ? edge2 : edge1);
  }

  public ResizeFixture resize(@NonNull SegmentType edge) {
    return new ResizeFixture(this,
                             edge.isHorizontal() ? edge : null,
                             edge.isHorizontal() ? null : edge);
  }

  public DragFixture drag() {
    return new DragFixture(singleton());
  }

  public ComponentFixture expectWidth(@NonNull String width) {
    assertEquals("Wrong width", width, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), ANDROID_URI, ATTR_LAYOUT_WIDTH));
    return this;
  }

  public ComponentFixture expectHeight(@NonNull String height) {
    assertEquals("Wrong height", height, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), ANDROID_URI, ATTR_LAYOUT_HEIGHT));
    return this;
  }

  public ComponentFixture expectAttribute(@NonNull String name, @NonNull String value) {
    assertEquals("Wrong " + name, value, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), ANDROID_URI, name));
    return this;
  }

  public ComponentFixture expectAttribute(@NonNull String namespace, @NonNull String name, @NonNull String value) {
    assertEquals("Wrong " + name, value, AndroidPsiUtils.getAttributeSafely(myComponent.getTag(), namespace, name));
    return this;
  }

  @NonNull
  public ComponentFixture expectXml(@NonNull @Language("XML") String xml) {
    assertEquals(xml, myComponent.getTag().getText());
    return this;
  }

  @NonNull
  public ScreenView getScreen() {
    return myScreenFixture.getScreen();
  }

  @NonNull
  public NlComponent getComponent() {
    return myComponent;
  }

  @NonNull
  public ComponentFixture parent() {
    assertNotNull(myComponent.getParent());
    return new ComponentFixture(myScreenFixture, myComponent.getParent());
  }

  @NonNull
  public ComponentFixture expectHierarchy(@NonNull String hierarchy) {
    String tree = NlComponent.toTree(Collections.singletonList(myComponent));
    assertEquals(tree, hierarchy);
    return this;
  }
}
