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
package com.android.tools.idea.naveditor.model;

import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * In the navigation editor {@link AndroidCoordinate} and {@link AndroidDpCoordinate} are equivalent, and neither are related to "Android"
 * at all.
 * Values annotated with {@code NavCoordinate} can be used in place of either in the context of the navigation editor.
 *
 * TODO: this should be replaced by a general (non-nav-specific) monitor-DPI independant coordinate system once that exists.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({METHOD, PARAMETER, LOCAL_VARIABLE, FIELD})
public @interface NavCoordinate {
}
