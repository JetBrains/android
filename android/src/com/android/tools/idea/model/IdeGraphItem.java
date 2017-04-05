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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.level2.GraphItem;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a deep copy of {@link GraphItem}.
 *
 * @see IdeAndroidProject
 */
public class IdeGraphItem implements GraphItem, Serializable {
  @NotNull private final String myArtifactAddress;
  @NotNull private final List<GraphItem> myDependencies;
  @Nullable private final String myRequestedCoordinates;

  public IdeGraphItem(@NotNull GraphItem item) {
    myArtifactAddress = item.getArtifactAddress();

    myDependencies = new ArrayList<>();
    for (GraphItem dependency : item.getDependencies()) {
      myDependencies.add(new IdeGraphItem(dependency));
    }

    myRequestedCoordinates = item.getRequestedCoordinates();
  }

  @Override
  @NotNull
  public String getArtifactAddress() {
    return myArtifactAddress;
  }

  @Override
  @NotNull
  public List<GraphItem> getDependencies() {
    return myDependencies;
  }

  @Override
  @Nullable
  public String getRequestedCoordinates() {
    return myRequestedCoordinates;
  }
}
