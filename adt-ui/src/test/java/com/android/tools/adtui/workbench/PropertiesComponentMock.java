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
package com.android.tools.adtui.workbench;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;

public class PropertiesComponentMock extends PropertiesComponent {
  private final Properties myProperties;

  public PropertiesComponentMock() {
    myProperties = new Properties();
  }

  @Override
  public void unsetValue(@NotNull String name) {
    myProperties.remove(name);
  }

  @Override
  public boolean isValueSet(@NotNull String name) {
    return myProperties.getProperty(name) != null;
  }

  @Nullable
  @Override
  public String getValue(@NonNls String name) {
    return myProperties.getProperty(name);
  }

  @Override
  public void setValue(@NotNull String name, @Nullable String value) {
    myProperties.setProperty(name, value);
  }

  @Override
  public void setValue(@NotNull String name, @Nullable String value, @Nullable String defaultValue) {
    if (value == null || value.equals(defaultValue)) {
      myProperties.remove(name);
    }
    else {
      setValue(name, String.valueOf(value));
    }
  }

  @Override
  public void setValue(@NotNull String name, float value, float defaultValue) {
    if (value == defaultValue) {
      myProperties.remove(name);
    }
    else {
      setValue(name, String.valueOf(value));
    }
  }

  @Override
  public void setValue(@NotNull String name, int value, int defaultValue) {
    if (value == defaultValue) {
      myProperties.remove(name);
    }
    else {
      setValue(name, String.valueOf(value));
    }
  }

  @Override
  public void setValue(@NotNull String name, boolean value, boolean defaultValue) {
    if (value == defaultValue) {
      myProperties.remove(name);
    }
    else {
      setValue(name, String.valueOf(value));
    }
  }

  @Nullable
  @Override
  public String[] getValues(@NotNull String name) {
    String value = myProperties.getProperty(name);
    if (value == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return value.split(",");
  }

  @Override
  public void setValues(@NotNull String name, @Nullable String[] values) {
    if (values == null || values.length == 0) {
      myProperties.setProperty(name, null);
    }
    StringBuilder builder = new StringBuilder();
    for (String value : values) {
      if (builder.length() == 0) {
        builder.append(",");
      }
      builder.append(value);
    }
    myProperties.setProperty(name, builder.toString());
  }
}
