/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import org.jetbrains.annotations.NotNull;

public class VectorPathBuilder {

  private StringBuilder stringBuilder;

  public VectorPathBuilder() {
    stringBuilder = new StringBuilder();
  }

  public StringBuilder getStringBuilder() {
    return stringBuilder;
  }

  public String getString() {
    return stringBuilder.toString();
  }

  public void append(char command) {
    stringBuilder.append(command);
  }

  public void appendPointCoordinates(@NotNull StringPoint coords) {

    stringBuilder.append(coords.getX()).append(",").append(coords.getY()).append(" ");
  }

  public void appendCommandAndCoordinates(char command, @NotNull StringPoint coords) {

    stringBuilder.append(command).append(coords.getX()).append(",").append(coords.getY()).append(" ");
  }

  public void appendCommandAndCoordinate(char command, @NotNull String coord) {
    stringBuilder.append(command).append(coord).append(" ");
  }
}
