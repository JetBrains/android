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
package com.android.tools.idea.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.AaptOptions;
import java.util.Objects;

public class AaptOptionsStub extends BaseStub implements AaptOptions {
    @NonNull private final Namespacing namespacing;

    public AaptOptionsStub() {
        this(Namespacing.DISABLED);
    }

    public AaptOptionsStub(@NonNull Namespacing namespacing) {
        this.namespacing = namespacing;
    }

    @Override
    @NonNull
    public Namespacing getNamespacing() {
        return namespacing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AaptOptions)) {
            return false;
        }
        AaptOptions that = (AaptOptions) o;
        return getNamespacing() == that.getNamespacing();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNamespacing());
    }
}
