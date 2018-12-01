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

public final class ModelWithIds {
  public final int m_length; // will generate id "length"
  public final int mSize; // will generate id "size"
  public final int _sum; // will generate id "sum"
  public final int value; // will generate id "value"

  public int getCount() { return 0;} // will generate id "count"
  public void setText(String text) {} // will generate id "text"
  public boolean isEnabled() { return true; } // will generate id "enabled"
}
