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
package com.android.tools.idea.gradle.dsl.api.ext;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

public class RawText {
  @NotNull private final String myKtsRawText;
  @NotNull private final String myGroovyRawText;

  public RawText(@NotNull String groovyRawText, @NotNull String ktsRawText) {
    myGroovyRawText = groovyRawText;
    myKtsRawText = ktsRawText;
  }

  @NotNull
  public String getKtsText() {
    return myKtsRawText;
  }

  @NotNull
  public String getGroovyText() {
    return myGroovyRawText;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RawText text = (RawText)o;
    return Objects.equal(myGroovyRawText, text.myGroovyRawText) && Objects.equal(myKtsRawText, text.myKtsRawText);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myGroovyRawText) + Objects.hashCode(myKtsRawText);
  }

  @Override
  public String toString() {
    return Objects.equal(myKtsRawText, myGroovyRawText) ? myKtsRawText : "KTS: " + myKtsRawText + ", Groovy: " + myGroovyRawText;
  }
}
