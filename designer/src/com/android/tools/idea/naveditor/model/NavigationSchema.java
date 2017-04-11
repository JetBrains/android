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
package com.android.tools.idea.naveditor.model;

import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides information on OOTB and user-specified navigation tags and attributes.
 *
 * TODO: implement for real once there is a way to do so.
 * We need to look at the app code and libraries and try to find subclasses of NavDestination and find their tag name and styleable name
 * (mechanism TBD).
 */
public class NavigationSchema {
  @VisibleForTesting
  public static final DestinationTag TAG_FRAGMENT = new DestinationTag("fragment", DestinationType.FRAGMENT);
  @VisibleForTesting
  public static final DestinationTag TAG_ACTIVITY = new DestinationTag("activity", DestinationType.ACTIVITY);
  @VisibleForTesting
  public static final DestinationTag TAG_NAVIGATION = new DestinationTag("navigation", DestinationType.NAVIGATION);

  public static final String TAG_ACTION = "action";
  public static final String ATTR_DESTINATION = "destination";

  private final Map<String, DestinationType> myTagToDestination;

  public enum DestinationType {
    NAVIGATION,
    FRAGMENT,
    ACTIVITY,
    OTHER
  }

  public static class DestinationTag {
    public final String tag;
    public final DestinationType type;

    DestinationTag(@NotNull String tag, @NotNull DestinationType type) {
      this.tag = tag;
      this.type = type;
    }
  }

  public NavigationSchema(@Nullable Project project) {
    // TODO
    myTagToDestination = getDestinationTags().stream().collect(Collectors.toMap(tag -> tag.tag, tag -> tag.type));
  }

  @NotNull
  public Set<DestinationTag> getDestinationTags() {
    // TODO
    return ImmutableSet.of(TAG_NAVIGATION, TAG_ACTIVITY, TAG_FRAGMENT);
  }

  @Nullable
  public DestinationType getDestinationType(@NotNull String tag) {
    return myTagToDestination.get(tag);
  }
}
