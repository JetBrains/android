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
package com.android.tools.rendering.security;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/** Exception thrown when custom view code makes an illegal code while rendering under layoutlib */
public class RenderSecurityException extends SecurityException {

    private final String myMessage;

    /** Use one of the create factory methods */
    private RenderSecurityException(@NonNull String message) {
        super(message);
        myMessage = message;
    }

    @Override
    public String getMessage() {
        return myMessage;
    }

    @Override
    public String toString() {
        // super prepends the fully qualified name of the exception
        return getMessage();
    }
    /**
     * Creates a new {@linkplain RenderSecurityException}
     *
     * @param resource the type of resource being accessed - "Thread", "Write", "Socket", etc
     * @param context more information about the object, such as the path of the file being read
     * @return a new exception
     */
    @NonNull
    public static RenderSecurityException create(@NonNull String resource,
            @Nullable String context) {
        return new RenderSecurityException(computeLabel(resource, context));
    }

    /**
     * Creates a new {@linkplain RenderSecurityException}
     *
     * @param message the message for the exception
     * @return a new exception
     */
    @NonNull
    static RenderSecurityException create(@NonNull String message) {
        return new RenderSecurityException(message);
    }

    private static String computeLabel(@NonNull String resource, @Nullable String context) {
        StringBuilder sb = new StringBuilder(40);
        sb.append(resource);
        sb.append(" access not allowed during rendering");
        if (context != null) {
            sb.append(" (").append(context).append(")");
        }
        return sb.toString();
    }
}
