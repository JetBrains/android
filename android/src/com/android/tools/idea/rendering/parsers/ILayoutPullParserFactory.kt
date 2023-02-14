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
package com.android.tools.idea.rendering.parsers

import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.util.PathString
import com.android.tools.idea.res.ResourceRepositoryManager

/**
 * A factory for creating [ILayoutPullParser]s.
 */
interface ILayoutPullParserFactory {
  /**
   * Creates a parser for the given XML file and returns it. May return null to indicate that this
   * factory does not provide a parser for the given file.
   */
  fun create(xml: PathString, layoutlibCallback: LayoutlibCallback, resourceRepositoryManager: ResourceRepositoryManager): ILayoutPullParser?
}
