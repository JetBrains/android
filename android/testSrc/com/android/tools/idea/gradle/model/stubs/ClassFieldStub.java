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
import com.android.builder.model.ClassField;
import com.android.tools.idea.gradle.model.UnusedModelMethodException;
import java.util.Objects;
import java.util.Set;

public final class ClassFieldStub extends BaseStub implements ClassField {
    @NonNull private final String myName;
    @NonNull private final String myType;
    @NonNull private final String myValue;

    public ClassFieldStub() {
        this("name", "type", "value");
    }

    public ClassFieldStub(@NonNull String name, @NonNull String type, @NonNull String value) {
        myName = name;
        myType = type;
        myValue = value;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getType() {
        return myType;
    }

    @Override
    @NonNull
    public String getValue() {
        return myValue;
    }

    @Override
    @NonNull
    public String getDocumentation() {
        throw new UnusedModelMethodException("getDocumentation");
    }

    @Override
    @NonNull
    public Set<String> getAnnotations() {
        throw new UnusedModelMethodException("getAnnotations");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClassField)) {
            return false;
        }
        ClassField stub = (ClassField) o;
        return Objects.equals(getName(), stub.getName())
                && Objects.equals(getType(), stub.getType())
                && Objects.equals(getValue(), stub.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getType(), getValue());
    }

    @Override
    public String toString() {
        return "ClassFieldStub{"
                + "myName='"
                + myName
                + '\''
                + ", myType='"
                + myType
                + '\''
                + ", myValue='"
                + myValue
                + '\''
                + "}";
    }
}
