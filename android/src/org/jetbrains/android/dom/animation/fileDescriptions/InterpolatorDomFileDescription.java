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
package org.jetbrains.android.dom.animation.fileDescriptions;

import com.android.resources.ResourceFolderType;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.android.dom.AbstractMultiRootFileDescription;
import org.jetbrains.android.dom.animation.InterpolatorElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class InterpolatorDomFileDescription extends AbstractMultiRootFileDescription<InterpolatorElement> {
  /**
   * Map contains name of a styleable with attributes by a tag name.
   * If key maps to {@link Optional#empty()} it means that such tag exists but doesn't have any attributes.
   */
  public static final ImmutableMap<String, Optional<String>> STYLEABLE_BY_TAG =
    ImmutableMap.<String, Optional<String>>builder()
      .put("linearInterpolator", Optional.empty())
      .put("accelerateInterpolator", Optional.of("AccelerateInterpolator"))
      .put("decelerateInterpolator", Optional.of("DecelerateInterpolator"))
      .put("accelerateDecelerateInterpolator", Optional.empty())
      .put("cycleInterpolator", Optional.of("CycleInterpolator"))
      .put("anticipateInterpolator", Optional.of("AnticipateInterpolator"))
      .put("overshootInterpolator", Optional.of("OvershootInterpolator"))
      .put("anticipateOvershootInterpolator", Optional.of("AnticipateOvershootInterpolator"))
      .put("bounceInterpolator", Optional.empty())
      .put("pathInterpolator", Optional.of("PathInterpolator"))
      .build();

  public InterpolatorDomFileDescription() {
    super(InterpolatorElement.class, ResourceFolderType.ANIM, STYLEABLE_BY_TAG.keySet());
  }

  @Nullable
  public static String getInterpolatorStyleableByTagName(@NotNull String tagName) {
    final Optional<String> optional = STYLEABLE_BY_TAG.get(tagName);

    if (optional != null && optional.isPresent()) {
      return optional.get();
    }
    return null;
  }
}
