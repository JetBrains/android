/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering.security

/**
 * Interface to support both the old (RenderSecurityManager) and the new (RenderSandbox) mechanism during the transition. Once
 * RenderSecurityManager is removed, this interface will likely not be needed anymore.
 */
interface RenderSecurity {
  fun activate(credential: Any)

  fun deactivate(credential: Any)
}
