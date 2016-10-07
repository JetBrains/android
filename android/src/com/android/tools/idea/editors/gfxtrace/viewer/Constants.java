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
package com.android.tools.idea.editors.gfxtrace.viewer;

public interface Constants {
  double SCENE_SCALE_FACTOR = 2.1;
  String POSITION_ATTRIBUTE = "aVertexPosition";
  String NORMAL_ATTRIBUTE = "aVertexNormal";
  String MODEL_VIEW_UNIFORM = "uModelView";
  String MODEL_VIEW_PROJECTION_UNIFORM = "uModelViewProj";
  String NORMAL_MATRIX_UNIFORM = "uNormalMatrix";
  String INVERT_NORMALS_UNIFORM = "uInvertNormals";

  // Camera constants.
  int STANDARD_WIDTH = 36;
  int STANDARD_HEIGHT = 24;
  int NEAR_FOCAL_LENGTH = 105;
  int FAR_FOCAL_LENGTH = 55;
  double MIN_DISTANCE = 3;
  double MAX_DISTANCE = 4.5;
  double Z_NEAR = 0.1;
}
