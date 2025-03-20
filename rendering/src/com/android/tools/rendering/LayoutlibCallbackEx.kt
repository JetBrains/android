/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.rendering

import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.tools.rendering.parsers.TagSnapshot

/**
 * Interface with the extensions used internally by Android Studio of the LayoutlibCallback to
 * manage the lifecyle and some settings of [LayoutlibCallbackImpl].
 */
internal abstract class LayoutlibCallbackEx : LayoutlibCallback() {
  /**
   * Returns whether the loader has received requests to load custom views. Note that the custom
   * view loading may not actually have succeeded; this flag only records whether it was
   * <b>requested</b>.
   *
   * <p/>
   * This allows to efficiently only recreate when needed upon code change in the project.
   *
   * @return true if the loader has been asked to load custom views
   */
  abstract fun isUsed(): Boolean

  /**
   * Load and parse the R class such that resource references in the layout rendering can refer to
   * local resources properly.
   *
   * <p>This only needs to be done if the build system compiles code of the given module against
   * R.java files generated with final fields, which will cause the chosen numeric resource ids to
   * be inlined into the consuming code. In this case we treat the R class bytecode as the source of
   * truth for mapping resources to numeric ids.
   */
  abstract fun loadAndParseRClass(): Unit

  /**
   * Sets the {@link ILayoutLog} logger to use for error messages during problems.
   *
   * @param logger the new logger to use
   */
  abstract fun setLogger(logger: IRenderLogger): Unit

  abstract fun getLayoutEmbeddedParser(): ILayoutPullParser?

  /** Resets the callback state for another render */
  abstract fun reset(): Unit

  /** Sets the resources to be used via `@aapt` resource references. */
  abstract fun setAaptDeclaredResources(resources: MutableMap<String, TagSnapshot>)

  /**
   * Sets the layout name and the parser. This is the parser that will be returned when
   * [LayoutlibCallback.getParser] is invoked.
   */
  abstract fun setLayoutParser(layoutName: String, modelParser: ILayoutPullParser)
}
