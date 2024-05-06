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
import com.intellij.util.ArrayUtilRt;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public String getValue(@NotNull @NonNls String name) {
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
      setValue(name, value);
    }
  }

  @Override
  public void setValue(@NotNull String name, float value, float defaultValue) {
    //noinspection FloatingPointEquality : Same as the implementation PropertiesComponentImpl
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
      return null;
    }
    return value.split("\n");
  }

  @Override
  public void setValues(@NotNull String name, @Nullable String[] values) {
    if (values == null || values.length == 0) {
      myProperties.setProperty(name, null);
    }
    StringBuilder builder = new StringBuilder();
    for (String value : values) {
      if (builder.length() > 0) {
        builder.append("\n");
      }
      builder.append(value);
    }
    myProperties.setProperty(name, builder.toString());
  }

  @Nullable
  @Override
  public List<String> getList(@NotNull String name) {
    String[] values = getValues(name);
    return values != null ? List.of(values) : null;
  }

  @Override
  public void setList(@NotNull String name, @Nullable Collection<String> values) {
    setValues(name, values != null ? values.toArray(ArrayUtilRt.EMPTY_STRING_ARRAY) : null);
  }

  @Override
  public boolean updateValue(@NotNull @NonNls String name, boolean newValue) {
    var val = String.valueOf(newValue);
    return !val.equals(myProperties.setProperty(name, val));
  }
}
