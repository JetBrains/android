/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.appspot.gsamplesindex.samplesindex.model;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public final class Screenshot extends GenericJson {
  @Key
  private String link;
  @Key
  private Boolean primary;

  public Screenshot() {
  }

  public String getLink() {
    return this.link;
  }

  public Screenshot setLink(String link) {
    this.link = link;
    return this;
  }

  public Boolean getPrimary() {
    return this.primary;
  }

  public Screenshot setPrimary(Boolean primary) {
    this.primary = primary;
    return this;
  }

  public Screenshot set(String fieldName, Object value) {
    return (Screenshot)super.set(fieldName, value);
  }

  public Screenshot clone() {
    return (Screenshot)super.clone();
  }
}
