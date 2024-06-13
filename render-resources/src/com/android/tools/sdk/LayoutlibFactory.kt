/*
 * Copyright (C) 2024 The Android Open Source Project
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
@file:JvmName("LayoutlibFactory")
package com.android.tools.sdk

import com.android.sdklib.IAndroidTarget
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.idea.layoutlib.RenderingException
import com.android.tools.layoutlib.LayoutlibContext

@Throws(RenderingException::class)
fun getLayoutLibrary(target: IAndroidTarget, platform: AndroidPlatform, context: LayoutlibContext): LayoutLibrary {
  return AndroidTargetData.get(platform.sdkData, target).getLayoutLibrary(context::register) { context.hasLayoutlibCrash() }
}