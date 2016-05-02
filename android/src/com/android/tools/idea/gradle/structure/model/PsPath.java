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
package com.android.tools.idea.gradle.structure.model;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.structure.model.PsPath.TexType.FOR_COMPARE_TO;

public abstract class PsPath implements Comparable<PsPath> {
  @NonNls public static final String GO_TO_PATH_TYPE = "psdGoTo://";
  @NonNls public static final String QUICK_FIX_PATH_TYPE = "psdFix://";

  @NotNull
  public static final PsPath EMPTY_PATH = new PsPath() {
    @Override
    @NotNull
    public String toText(@NotNull TexType type) {
      return "";
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    public String toString() {
      return "<Empty Path>";
    }
  };

  @Override
  public int compareTo(PsPath path) {
    return toText(FOR_COMPARE_TO).compareTo(path.toText(FOR_COMPARE_TO));
  }

  @NotNull
  public abstract String toText(@NotNull TexType type);

  @Override
  public String toString() {
    return toText(FOR_COMPARE_TO);
  }

  public enum TexType {
    PLAIN_TEXT, HTML, FOR_COMPARE_TO
  }
}
