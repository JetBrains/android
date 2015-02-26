/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation.model;

public class Transition {
  public static final String PRESS = "Press";
  @SuppressWarnings("UnusedDeclaration")
  public static final String SWIPE = "Swipe";

  private String type;
  private final Locator source;
  private final Locator destination;

  public Transition(String type, Locator source, Locator destination) {
    this.type = type;
    this.source = source;
    this.destination = destination;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Locator getSource() {
    return source;
  }

  public Locator getDestination() {
    return destination;
  }

  @Override
  public String toString() {
    return "Transition{" +
           "type='" + type + '\'' +
           ", source=" + source +
           ", destination=" + destination +
           '}';
  }
}
