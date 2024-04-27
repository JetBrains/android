/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.dom

import com.android.SdkConstants
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.Namespace
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription

/**
 * Attribute in the android XML namespace.
 *
 * @see AndroidResourceDomFileDescription.initializeFileDescription
 */
@Namespace(SdkConstants.ANDROID_NS_NAME)
interface AndroidAttributeValue<T> : GenericAttributeValue<T>

/**
 * Attribute in the res-auto XML namespace.
 *
 * @see AndroidResourceDomFileDescription.initializeFileDescription
 */
@Namespace(SdkConstants.APP_PREFIX) interface ResAutoAttributeValue<T> : GenericAttributeValue<T>

/**
 * Attribute in the tools XML namespace.
 *
 * @see NavigationDomFileDescription.initializeFileDescription
 */
@Namespace(SdkConstants.TOOLS_PREFIX) interface ToolsAttributeValue<T> : GenericAttributeValue<T>
