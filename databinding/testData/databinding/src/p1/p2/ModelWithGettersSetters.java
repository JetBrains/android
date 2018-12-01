/*
 * Copyright (C) 2018 The Android Open Source Project
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
package p1.p2;

public final class ModelWithGettersSetters {
  // Valid getter and setter formats
  boolean getBoolValue() { return true; }
  int getIntValue() { return 123; }
  String getStringValue() { return "Not used"; }
  boolean isBoolValue() { return true; }
  void setBoolValue(boolean value) {}
  void setIntValue(int value) {}
  void setStringValue(String value) {}

  // Invalid getters

  void getVoidValue() {} // getter can't return void
  int isIntValue() { return 9000; } // "is" getter must return boolean
  int getIntValue(int arg1, int arg2) { return arg1 + arg2}; // getter should take 0 params
  int get456() { return 456; } // Part after "get" should be a valid java identifier
  boolean is789() { return false; } // Part after "get" should be a valid java identifier

  // Invalid setters

  void setBoolValue() {} // Setter should take a single parameter
  int setIntValue(int value) { return value; } // Setter should return void
  void setStringValue(String value, boolean isBold) {} // Setter should take a single parameter
  void set321() { } // Part after "set" should be a valid java identifier

  // Misc. functions that aren't setters or getters

  void logErrors() {}
  boolean update() { return true; }
  int length() { return 20; }
}
