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
package com.android.tools.profilers

import java.util.*

class FakeProfilerPreferences : ProfilerPreferences {

  private val myMap = HashMap<String, String>()

  override fun getValue(name: String, defaultValue: String): String {
    return myMap.getOrDefault(name, defaultValue)
  }

  override fun getFloat(name: String, defaultValue: Float): Float {
    return myMap[name]?.toFloatOrNull() ?: defaultValue
  }

  override fun getInt(name: String, defaultValue: Int): Int {
    return myMap[name]?.toIntOrNull() ?: defaultValue
  }

  override fun getBoolean(name: String, defaultValue: Boolean): Boolean {
    return myMap[name]?.toBoolean() ?: defaultValue
  }

  override fun setValue(name: String, value: String) {
    myMap.put(name, value)
  }

  override fun setFloat(name: String, value: Float) {
    setFloat(name, value, 0f)
  }

  override fun setFloat(name: String, value: Float, defaultValue: Float) {
    if (java.lang.Float.compare(value, defaultValue) == 0) {
      myMap.remove(name)
    }
    else {
      myMap.put(name, java.lang.Float.toString(value))
    }
  }

  override fun setInt(name: String, value: Int) {
    setInt(name, value, 0)
  }

  override fun setInt(name: String, value: Int, defaultValue: Int) {
    if (value == defaultValue) {
      myMap.remove(name)
    }
    else {
      myMap.put(name, Integer.toString(value))
    }
  }

  override fun setBoolean(name: String, value: Boolean) {
    myMap.put(name, java.lang.Boolean.toString(value))
  }
}
