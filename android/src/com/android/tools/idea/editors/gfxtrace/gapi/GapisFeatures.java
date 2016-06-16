/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.gapi;

import java.util.HashSet;
import java.util.Set;

/**
 * This class exposes the set of features supported by the GAPIS instance.
 */
public class GapisFeatures {
  private static final String FEATURE_RPC_STRING_TABLES = "rpc-string-tables";
  private static final String FEATURE_CONTEXTS_AND_HIERACHIES = "contexts-hierachies";
  private static final String FEATURE_MESHES = "meshes";

  private final Set<String> myFeatures = new HashSet<String>();

  public void setFeatureList(String[] featureList) {
    for (String feature : featureList) {
      myFeatures.add(feature);
    }
  }

  /**
   * Returns true if the GAPIS instance supports the RPCs:
   * <ul>
   *  <li>{@code GetAvailableStringTables}
   *  <li>{@code GetStringTable}
   * </ul>
   */
  public boolean hasRpcStringTables() {
    return myFeatures.contains(FEATURE_RPC_STRING_TABLES);
  }

  /**
   * Returns true if the GAPIS instance supports getting the following paths:
   * <ul>
   *  <li>{@link com.android.tools.idea.editors.gfxtrace.service.path.ContextsPath}
   *  <li>{@link com.android.tools.idea.editors.gfxtrace.service.path.HierarchiesPath}
   * </ul>
   */
  public boolean hasContextsAndHierachies() {
    return myFeatures.contains(FEATURE_CONTEXTS_AND_HIERACHIES);
  }

  /**
   * Returns true if the GAPIS instance supports getting the following paths:
   * <ul>
   *   <li>{@link com.android.tools.idea.editors.gfxtrace.service.path.MeshPath}
   *   <li>{@link com.android.tools.idea.editors.gfxtrace.service.path.VertexStreamDataPath}
   * </ul>
   */
  public boolean hasMeshes() {
    return myFeatures.contains(FEATURE_MESHES);
  }
}
