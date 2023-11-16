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
package com.android.tools.idea.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.model.UnusedModelMethodException;
import java.util.Objects;
import java.util.function.Function;

public class BaseStub {
    protected <T> boolean equals(@NonNull T other, @NonNull Function<T, Object> function) {
        try {
            //noinspection unchecked
            return Objects.equals(function.apply((T) this), function.apply(other));
        } catch (UnusedModelMethodException ignored) {
            return true;
        }
    }
}
