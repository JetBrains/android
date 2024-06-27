/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom;

import org.jetbrains.android.facet.AndroidFacet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to annotate XML model interfaces with styleable name which should be used to
 * get attributes from.
 *
 * For XML tags whose attributes are described in a styleable definition, which name is known statically,
 * {@link Styleable} annotation can be used with corresponding interface extending {@link com.intellij.util.xml.DomElement}.
 * For example, interface corresponding to "include" tag is {@link org.jetbrains.android.dom.layout.Include},
 * and its attributes are coming from "Include" and "ViewGroup_Layout" styleable definitions. Thus, Include
 * interface is annotated with {@code @Styleable({"Include", "ViewGroup_Layout"})}
 *
 * @see AttributeProcessingUtil#processAttributes(AndroidDomElement, AndroidFacet, boolean, AttributeProcessingUtil.AttributeProcessor)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Styleable {
  String[] value();
  String packageName() default "android";
  String[] skippedAttributes() default {};
}
