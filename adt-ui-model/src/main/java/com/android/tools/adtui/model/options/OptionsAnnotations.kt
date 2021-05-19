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
package com.android.tools.adtui.model.options

/**
 * Name of the default group properties are apart of.
 */
const val DEFAULT_GROUP = ""

/**
 * Default order value or properties in layout. Starting with a non-zero value allows easier control for moving elements to the start
 * and end of the list.
 */
const val DEFAULT_ORDER = 100

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
/**
 * Property annotation that is used by the {@link OptionsPanel} when building the UI. Some property usage is dependent on the implementation
 * by the {@OptionsBinder}. These comments are general guidelines and true for the built in binders.
 * {@param name} is typically displayed before the input control.
 * {@param description} is typically displayed disabled under the current active control for the given property.
 * {@param group} name of properties that should be viewed as a collection together. Properties are grouped by the {@param group} name then
 * ordered within each group using the {@param order} value.
 * {@param unit} used by some {@OptionsBinders} to display unit type information. Eg Mb (Megabytes).
 * {@param order} used by the layout to determine position in list. Lower = higher.
 */
annotation class OptionsProperty(val name: String = "",
                                 val description: String = "",
                                 val group: String = DEFAULT_GROUP,
                                 val unit: String = "",
                                 val order: Int = DEFAULT_ORDER)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
/**
 * Common slider control. This control expects the accessor/mutator return type to be "int".
 * {@param min} is the lowest value the slider will move to.
 * {@param max} is the highest value the slider will move to.
 * {@param step} is the increment amount the slider will move when using the arrows.
 */
annotation class Slider(val min: Int, val max: Int, val step: Int)