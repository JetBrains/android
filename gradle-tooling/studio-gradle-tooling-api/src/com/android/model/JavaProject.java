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

import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for the model of Java Library Projects.
 */
public interface JavaProject {

    /**
     * Returns the model version.
     *
     * @return a long integer representing the model version.
     */
    long getModelVersion();

    /**
     * Returns the name of the module.
     *
     * @return the name of the module.
     */
    @NotNull
    String getName();

    /**
     * Returns a list of {@link SourceSet}.
     *
     * @return a list of {@link SourceSet}.
     */
    @NotNull
    Collection<SourceSet> getSourceSets();

    /**
     * Returns the level of java language.
     *
     * @return the java language level.
     */
    @NotNull
    String getJavaLanguageLevel();
}
