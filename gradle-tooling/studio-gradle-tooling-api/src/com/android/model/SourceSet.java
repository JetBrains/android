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
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a SourceSet in Java plugin, contains source folders, as well as a list of dependencies
 * of compileClasspath and runtimeClasspath configuration.
 */
public interface SourceSet {

    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    @NotNull
    String getName();

    /**
     * Returns the java source folders.
     *
     * @return a list of folder paths. They may not all exist.
     */
    @NotNull
    Collection<File> getSourceDirectories();

    /**
     * Returns the java resources folders.
     *
     * @return a list of folder paths. They may not all exist.
     */
    @NotNull
    Collection<File> getResourcesDirectories();

    /**
     * Returns the classes compiler output folder.
     *
     * @return the classes compiler output folder.
     */
    @NotNull
    Collection<File> getClassesOutputDirectories();

    /**
     * Returns the resources compiler output folder.
     *
     * @return resources output folder.
     */
    @Nullable
    File getResourcesOutputDirectory();

    /**
     * Returns a collection of dependencies of compileClasspath configuration.
     *
     * @return a collection of dependencies of compileClasspath configuration.
     */
    @NotNull
    Collection<JavaLibrary> getCompileClasspathDependencies();

    /**
     * Returns a collection of dependencies of runtimeClasspath configuration.
     *
     * @return a collection of dependencies of runtimeClasspath configuration.
     */
    @NotNull
    Collection<JavaLibrary> getRuntimeClasspathDependencies();
}
