/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
  public final String source;
  public final String view;
  public final String gesture;
  public final String destination;

  public Transition(@Property("source") String source,
                    @Property("view") String view,
                    @Property("gesture") String gesture,
                    @Property("destination") String destination) {
    this.source = source;
    this.view = view;
    this.gesture = gesture;
    this.destination = destination;
  }

  public String getSource() {
    return source;
  }

  public String getView() {
    return view;
  }

  public String getGesture() {
    return gesture;
  }

  public String getDestination() {
    return destination;
  }

  @Override
  public String toString() {
    return "Navigation{" +
           "source='" + source + '\'' +
           ", view='" + view + '\'' +
           ", gesture='" + gesture + '\'' +
           ", destination='" + destination + '\'' +
           '}';
  }
}
