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
package com.android.navigation;

import com.android.annotations.Property;

public class Transition {
  private String type;
  private String viewIdentifier;
  private State source;
  private State destination;

  public Transition(@Property("gesture")     String type,
                    @Property("source")      State source,
                    @Property("destination") State destination) {
    this.type = type;
    this.source = source;
    this.destination = destination;
  }

  public State getSource() {
    return source;
  }

  public void setSource(State source) {
    this.source = source;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public State getDestination() {
    return destination;
  }

  public void setDestination(State destination) {
    this.destination = destination;
  }

  public String getViewIdentifier() {
    return viewIdentifier;
  }

  public void setViewIdentifier(String viewIdentifier) {
    this.viewIdentifier = viewIdentifier;
  }

  @Override
  public String toString() {
    return "Navigation{" +
           "source='" + source + '\'' +
           ", gesture='" + type + '\'' +
           ", destination='" + destination + '\'' +
           '}';
  }
}
