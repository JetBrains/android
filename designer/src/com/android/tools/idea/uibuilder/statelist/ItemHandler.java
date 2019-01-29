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
package com.android.tools.idea.uibuilder.statelist;

import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.common.model.NlComponent;
import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;

public final class ItemHandler extends ViewHandler {
  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    newChild.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
    newChild.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);

    return true;
  }

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent item) {
    String drawable = Strings.nullToEmpty(item.getAndroidAttribute(ATTR_DRAWABLE));

    String states = item.getAttributes().stream()
      .filter(attribute -> State.valueOfString(attribute.name) != null)
      .filter(attribute -> Boolean.valueOf(attribute.value))
      .map(attribute -> attribute.name)
      .collect(Collectors.joining(", "));

    return states.isEmpty() ? drawable : drawable + " " + states;
  }

  @NotNull
  @Override
  public List<String> getInspectorProperties() {
    State[] states = State.values();

    List<String> attributes = new ArrayList<>(1 + states.length);
    attributes.add(ATTR_DRAWABLE);

    Arrays.stream(states)
      .map(State::toString)
      .forEach(attributes::add);

    return attributes;
  }
}