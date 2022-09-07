/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors

import com.intellij.psi.impl.JavaRegExpHost

class AndroidRegExpHost : JavaRegExpHost() {
  override fun characterNeedsEscaping(c: Char): Boolean {
    // The regex engine for Android comes from the external ICU project, which differs from the OpenJDK library. Documentation for escaped
    // characters is available at https://unicode-org.github.io/icu/userguide/strings/regexp.html, but the main point is that the right
    // bracket character ('}') needs to be escaped for the Android engine.
    //
    // At the time of this writing, there is no way to distinguish between characters that need to be escaped within a class/set versus
    // those that need to be escaped outside. (That issue is tracked by https://youtrack.jetbrains.com/issue/IDEA-301618). The difference is
    // as follows:
    //
    // Within a class: "[\\}]". The escape is redundant here, but the expression will still work.
    // Outside a class: "\\}". The escape is necessary here; if it is flagged as redundant and removed, the expression will break.
    //
    // Unfortunately, we currently must choose one or the other, since we don't know if the character is in a class or not. Therefore we're
    // choosing to mark it as needing an escape, since this results in an expression that still works.
    return c == '}' || super.characterNeedsEscaping(c)
  }
}
