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

package com.android.tools.idea.stats;

public class KeyString {
  private final String myKey;
  private final String myValue;

  public KeyString(String key, String value) {
    myKey = key;
    myValue = value;
  }

  public String getKey() {
    return myKey;
  }

  public String getValue() {
    return myValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    KeyString keyString = (KeyString)o;

    if (myKey != null ? !myKey.equals(keyString.myKey) : keyString.myKey != null) return false;
    if (myValue != null ? !myValue.equals(keyString.myValue) : keyString.myValue != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myKey != null ? myKey.hashCode() : 0;
    result = 31 * result + (myValue != null ? myValue.hashCode() : 0);
    return result;
  }

  // For debugging purposes
  @Override
  public String toString() {
    return "KeyString{" +
           "myKey='" + myKey + '\'' +
           ", myValue='" + myValue + '\'' +
           '}';
  }
}
