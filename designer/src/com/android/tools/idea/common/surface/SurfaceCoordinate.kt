/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.surface

/**
 * The independent scaling system in DesignSurface regardless DPI of display.
 * For example, when zoom level is 50% and ScreenScalingFactor is 2, this value is 50% / 2 = 25%
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.PROPERTY)
internal annotation class SurfaceScale

/**
 * These annotations are used in [DesignSurface] to improve the readability of the relationship between scaling and zoom level.
 */
/**
 * Percentage of scaling a.k.a. zoom level (25%, 33%, 50%, etc). This value consider HDPI of display.
 * This value is same as the zoom level shows in [DesignSurface].
 * SurfaceZoomLevel = Surface Scale * Screen Factor
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER)
internal annotation class SurfaceZoomLevel

/**
 * The screen factor of display. Usually this value is 1, but on HDPI display this value may be 2 (e.g. Mac's built-in display).
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER)
annotation class SurfaceScreenScalingFactor



