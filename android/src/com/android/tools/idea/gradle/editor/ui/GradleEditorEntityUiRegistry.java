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
package com.android.tools.idea.gradle.editor.ui;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * {@link GradleEditorEntityUi} registry.
 */
public class GradleEditorEntityUiRegistry {

  private final NotNullLazyValue<Set<GradleEditorEntityUi<?>>> myUis = new NotNullLazyValue<Set<GradleEditorEntityUi<?>>>() {
    @NotNull
    @Override
    protected Set<GradleEditorEntityUi<?>> compute() {
      Set<GradleEditorEntityUi<?>> result = Sets.newHashSet();
      result.add(new SimpleGradleEntityUi());
      result.add(new ExternalDependencyEntityUi());
      Collections.addAll(result, GradleEditorEntityUi.EP_NAME.getExtensions());
      return result;
    }
  };
  private final DummyGradleEditorEntityUi myDummyUi = new DummyGradleEditorEntityUi();

  @SuppressWarnings("unchecked")
  @NotNull
  public List<GradleEditorEntityUi<?>> getEntityUis(@NotNull final GradleEditorEntity entity) {
    // We filter registered entity UIs which know how to work with the given entity first and choose the most specific of them.
    // Example: C extends B, B extends A, UI1 is registered for the B.class and UI2 for the A.class, UI1 is returned
    // for an entity of type C.
    List<GradleEditorEntityUi<?>> candidates = ContainerUtil.filter(myUis.getValue(), new Condition<GradleEditorEntityUi<?>>() {
      @Override
      public boolean value(GradleEditorEntityUi<?> renderer) {
        return renderer.getTargetEntityClass().isInstance(entity);
      }
    });
    if (candidates.isEmpty()) {
      return Collections.<GradleEditorEntityUi<?>>singletonList(myDummyUi);
    }

    ExternalSystemApiUtil.orderAwareSort(candidates);
    return candidates;
  }

  /**
   * Allows to answer if there is a non-default {@link GradleEditorEntityUi} which might serve given entity.
   * <p/>
   * The difference between this method and {@link #getEntityUis(GradleEditorEntity)} is that the later returns generic default
   * implementation when no UI is found for the target entity.
   *
   * @param entity  an entity to check
   * @return        <code>true</code> if there is a non-default {@link GradleEditorEntityUi} found for the given entity;
   *                <code>false</code> otherwise
   */
  public boolean hasEntityUi(@NotNull final GradleEditorEntity entity) {
    return null != ContainerUtil.find(myUis.getValue(), new Condition<GradleEditorEntityUi<?>>() {
      @Override
      public boolean value(GradleEditorEntityUi<?> ui) {
        return ui.getTargetEntityClass().isInstance(entity);
      }
    });
  }
}
