/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.skincombobox;

import java.nio.file.Path;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public interface Skin extends Comparable<Skin> {
  @NotNull
  Comparator<Skin> ourComparator = Comparator.<Skin, Boolean>comparing(skin -> skin.equals(NoSkin.INSTANCE), Comparator.reverseOrder())
    .thenComparing(Object::toString, Collator.getInstance(Locale.ROOT));

  @NotNull
  Skin merge(@NotNull Skin skin);

  @NotNull
  Path path();

  @Override
  default int compareTo(@NotNull Skin skin) {
    return ourComparator.compare(this, skin);
  }
}
