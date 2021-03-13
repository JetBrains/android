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
package com.android.tools.idea.gradle.project.model;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import nl.jqno.equalsverifier.api.SingleTypeEqualsVerifierApi;
import org.junit.ComparisonFailure;

public final class IdeModelTestUtils {
    private IdeModelTestUtils() {}

    public static void expectUnsupportedOperationException(@NonNull Runnable methodInvocation) {
        try {
            methodInvocation.run();
            fail("Expecting UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Ignored.
        }
    }

    public static <T> void assertEqualsOrSimilar(@NonNull T original, @NonNull T copy)
            throws Throwable {
        for (Method methodInOriginal : original.getClass().getDeclaredMethods()) {
            if (isGetter(methodInOriginal)) {
                String name = methodInOriginal.getName();
                Method methodInCopy = copy.getClass().getMethod(name);
                Object valueInCopy;
                try {
                    valueInCopy = invokeMethod(methodInCopy, copy);
                } catch (InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (ignoreException(target)) {
                        continue;
                    }
                    throw target != null ? target : e;
                }
                Object valueInOriginal = invokeMethod(methodInOriginal, original);
                if (!Objects.equals(valueInOriginal, valueInCopy)) {
                    throw new ComparisonFailure(
                            name, Objects.toString(valueInOriginal), Objects.toString(valueInCopy));
                }
            }
        }
    }

    public static void verifyUsageOfImmutableCollections(@NonNull Object o) throws Throwable {
        for (Method method : o.getClass().getDeclaredMethods()) {
            if (isGetter(method)) {
                Object value;
                try {
                    value = invokeMethod(method, o);
                } catch (InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (ignoreException(target)) {
                        continue;
                    }
                    throw target != null ? target : e;
                }
                String name = method.getName();
                if (value instanceof List) {
                    if (value != Collections.EMPTY_LIST && !(value instanceof ImmutableList)) {
                        throw new AssertionError(
                                "List returned by method '" + name + "' is not immutable");
                    }
                    continue;
                }
                if (value instanceof Set) {
                    if (value != Collections.EMPTY_SET && !(value instanceof ImmutableSet)) {
                        throw new AssertionError(
                                "Set returned by method '" + name + "' is not immutable");
                    }
                    continue;
                }
                if (value instanceof Map) {
                    if (value != Collections.EMPTY_MAP && !(value instanceof ImmutableMap)) {
                        throw new AssertionError(
                                "Map returned by method '" + name + "' is not immutable");
                    }
                }
            }
        }
    }

    private static boolean isGetter(@NonNull Method method) {
        if (method.getParameterCount() != 0) {
            return false;
        }
        int modifiers = method.getModifiers();
        if (!isPublic(modifiers) || isStatic(modifiers)) {
            return false;
        }
        String name = method.getName();
        return name.startsWith("is") || (name.startsWith("get") && !name.equals("getClass"));
    }

    @Nullable
    private static Object invokeMethod(@NonNull Method method, @NonNull Object target)
            throws Throwable {
        boolean accessible = method.isAccessible();
        method.setAccessible(true);
        try {
            return method.invoke(target);
        } finally {
            method.setAccessible(accessible);
        }
    }

    private static boolean ignoreException(@Nullable Throwable e) {
        return e instanceof UnusedModelMethodException
                || e instanceof UnsupportedOperationException;
    }


    @NonNull
    public static <T extends Serializable> SingleTypeEqualsVerifierApi<T> createEqualsVerifier(
            @NonNull Class<T> type) {
        return createEqualsVerifier(type, "myHashCode");
    }

    @NonNull
    public static <T extends Serializable> SingleTypeEqualsVerifierApi<T> createEqualsVerifier(
            @NonNull Class<T> type, @NonNull String hashCodeField) {
        SingleTypeEqualsVerifierApi<T> equalsVerifier = EqualsVerifier.forClass(type);
        equalsVerifier
                .withCachedHashCode(hashCodeField, "calculateHashCode", null)
                .suppress(Warning.NO_EXAMPLE_FOR_CACHED_HASHCODE);
        return equalsVerifier;
    }
}
