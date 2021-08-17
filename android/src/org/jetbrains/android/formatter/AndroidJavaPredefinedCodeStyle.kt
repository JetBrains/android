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
package org.jetbrains.android.formatter

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.codeStyle.PackageEntry
import com.intellij.psi.codeStyle.PackageEntryTable
import com.intellij.psi.codeStyle.PredefinedCodeStyle

class AndroidJavaPredefinedCodeStyle : PredefinedCodeStyle("Android", JavaLanguage.INSTANCE) {

  override fun apply(settings: CodeStyleSettings) {
    val javaSettings = settings.getCustomSettings(JavaCodeStyleSettings::class.java)

    // Set Import order
    javaSettings.IMPORT_LAYOUT_TABLE.copyFrom(getAndroidImportOrder())

    // Don't use wildcard imports
    javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(PackageEntryTable())
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 99 // Class count to use import with "*"
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 99 // Name count to use static import with "*"

    // set margin to 100 columns
    settings.defaultRightMargin = 100
  }

  private fun getAndroidImportOrder(): PackageEntryTable {
    val table = PackageEntryTable()
    // From https://source.android.com/setup/contribute/code-style#order-import-statements,
    // the order should be:
    //  1. Android imports
    //  2. Imports from 3rd parties (com, junit, net, org)
    //  3. java and javax
    // Static imports above all the other imports ordered the same way as regular imports.
    table.addEntry(PackageEntry(true, "android", true))
    table.addEntry(PackageEntry(true, "androidx", true))
    table.addEntry(PackageEntry(true, "com", true))
    table.addEntry(PackageEntry(true, "junit", true))
    table.addEntry(PackageEntry(true, "net", true))
    table.addEntry(PackageEntry(true, "org", true))
    table.addEntry(PackageEntry(true, "java", true))
    table.addEntry(PackageEntry(true, "javax", true))
    table.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY)
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "android", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "androidx", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "com", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "junit", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "net", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "org", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "java", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry(false, "javax", true))
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)
    table.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY)
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY)

    return table
  }
}