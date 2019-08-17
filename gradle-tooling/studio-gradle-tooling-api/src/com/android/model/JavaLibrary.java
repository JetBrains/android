/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.model;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an entry of java dependency. It could be project dependency or external module
 * dependency.
 */
public interface JavaLibrary {

    /**
     * Returns an optional project identifier if the library is output by a module.
     *
     * @return the project identifier.
     */
    @Nullable
    String getProject();

    /**
     * Returns an optional build identifier if the library is output by a module.
     *
     * @return the build identifier.
     */
    @Nullable
    String getBuildId();

    /**
     * Returns a user friendly name.
     *
     * @return a user friendly name.
     */
    @NotNull
    String getName();

    /**
     * Returns the library's jar file.
     *
     * @return the library's jar file. Returns null for unresolved dependency and module dependency.
     */
    @Nullable
    File getJarFile();

    /**
     * Returns the source directory/archive for this dependency.
     *
     * @return The source file. Returns null when the source is not available for this dependency.
     */
    @Nullable
    File getSource();

    /**
     * Returns the Javadoc directory/archive for this dependency.
     *
     * @return The Javadoc file. Returns null when the Javadoc is not available for this dependency.
     */
    @Nullable
    File getJavadoc();

    /**
     * Returns the module version for external dependency.
     *
     * @return {@link LibraryVersion} for this dependency.
     */
    @Nullable
    LibraryVersion getLibraryVersion();
}
