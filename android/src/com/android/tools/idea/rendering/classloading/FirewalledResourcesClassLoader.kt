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
package com.android.tools.idea.rendering.classloading

import java.io.InputStream
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * Class loader that prevents the loading of any resources.
 *
 * This is meant to workaround b/151089727 and avoid the [RenderClassLoader] accessing resources from
 * the plugin class loader.
 */
class FirewalledResourcesClassLoader(parent: ClassLoader): ClassLoader(parent) {
  override fun findResource(name: String): URL? = null
  override fun findResources(name: String): Enumeration<URL> = Collections.emptyEnumeration()
  override fun findResource(moduleName: String, name: String): URL? = null
  override fun getResource(name: String): URL? = null
  override fun getResources(name: String): Enumeration<URL> = Collections.emptyEnumeration()
  override fun getResourceAsStream(name: String): InputStream? = null
}