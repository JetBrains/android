/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.ProfilerPreferences;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * per-studio session caching of key-value pairs.
 * To persist preferences across studio instances, use {@link IntellijProfilerPreferences} instead.
 */
public final class TemporaryProfilerPreferences implements ProfilerPreferences {
  @NotNull private static final Map<String, String> ourPreferenceMap = new HashMap<>();

  @NotNull
  @Override
  public String getValue(@NotNull String name, @NotNull String defaultValue) {
    if (ourPreferenceMap.containsKey(name)) {
      return ourPreferenceMap.get(name);
    }

    return defaultValue;
  }

  @Override
  public float getFloat(@NotNull String name, float defaultValue) {
    if (ourPreferenceMap.containsKey(name)) {
      try {
        return Float.parseFloat(ourPreferenceMap.get(name));
      }
      catch (NumberFormatException ignored) {
      }
    }

    return defaultValue;
  }

  @Override
  public int getInt(@NotNull String name, int defaultValue) {
    if (ourPreferenceMap.containsKey(name)) {
      try {
        return Integer.parseInt(ourPreferenceMap.get(name));
      }
      catch (NumberFormatException ignored) {
      }
    }

    return defaultValue;
  }

  @Override
  public boolean getBoolean(@NotNull String name, boolean defaultValue) {
    if (ourPreferenceMap.containsKey(name)) {
      return Boolean.parseBoolean(ourPreferenceMap.get(name));
    }

    return defaultValue;
  }

  @Override
  public void setValue(@NotNull String name, @NotNull String value) {
    ourPreferenceMap.put(name, value);
  }

  @Override
  public void setFloat(@NotNull String name, float value) {
    ourPreferenceMap.put(name, Float.toString(value));
  }

  @Override
  public void setInt(@NotNull String name, int value) {
    ourPreferenceMap.put(name, Integer.toString(value));
  }

  @Override
  public void setBoolean(@NotNull String name, boolean value) {
    ourPreferenceMap.put(name, Boolean.toString(value));
  }
}
