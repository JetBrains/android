/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.androidModule.res.values

import com.android.tools.idea.wizard.template.MaterialColor

fun androidModuleColors() = """
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      ${MaterialColor.PURPLE_200.xmlElement()}
      ${MaterialColor.PURPLE_500.xmlElement()}
      ${MaterialColor.PURPLE_700.xmlElement()}
      ${MaterialColor.TEAL_200.xmlElement()}
      ${MaterialColor.TEAL_700.xmlElement()}
      ${MaterialColor.BLACK.xmlElement()}
      ${MaterialColor.WHITE.xmlElement()}
  </resources>
"""
