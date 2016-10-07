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
package com.android.tools.idea.editors.hierarchyview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

public class HierarchyViewCaptureOptions {
  private static final String VERSION = "version";
  private static final String TITLE = "title";

  private int myVersion = 1;
  @NotNull private String myTitle = "";

  public int getVersion() {
    return myVersion;
  }

  public void setVersion(int version) {
    myVersion = version;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  public void setTitle(@NotNull String title) {
    myTitle = title;
  }

  @Override
  public String toString() {
    return serialize();
  }

  public String serialize() {
    JsonObject obj = new JsonObject();
    obj.addProperty(VERSION, myVersion);
    obj.addProperty(TITLE, myTitle);
    return obj.toString();
  }

  public void parse(String json) {
    JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
    setVersion(obj.get(VERSION).getAsInt());
    setTitle(obj.get(TITLE).getAsString());
  }
}
