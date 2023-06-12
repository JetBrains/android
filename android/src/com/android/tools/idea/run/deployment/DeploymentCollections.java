/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DeploymentCollections {
  private DeploymentCollections() {
  }

  static <E> @NotNull Collection<E> toList(@Nullable E element) {
    if (element == null) {
      return Collections.emptyList();
    }

    return Collections.singletonList(element);
  }

  static <E> @NotNull Optional<E> toOptional(@NotNull Collection<E> collection) {
    int size = collection.size();

    switch (size) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.of(collection.iterator().next());
      default:
        throw new IllegalArgumentException(Integer.toString(size));
    }
  }
}
