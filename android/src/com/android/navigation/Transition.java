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
  private final Locator source;
  private final Locator destination;

  public Transition(@Property("type")        String type,
                    @Property("source")      Locator source,
                    @Property("destination") Locator destination) {
    this.type = type;
    this.source = source;
    this.destination = destination;
  }

  public static Transition of(String type, State source, State destination) {
    return new Transition(type, new Locator(source), new Locator(destination));
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
}
