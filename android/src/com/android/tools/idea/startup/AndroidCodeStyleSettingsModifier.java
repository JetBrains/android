/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.startup;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings;
import org.jetbrains.android.formatter.AndroidXmlPredefinedCodeStyle;

public class AndroidCodeStyleSettingsModifier {
  public static void modify(CodeStyleSettings settings) {
    // Use Android XML formatter by default
    AndroidXmlCodeStyleSettings.getInstance(settings).USE_CUSTOM_SETTINGS = true;

    // XML:
    // Copy Android code style
    AndroidXmlPredefinedCodeStyle xmlStyle = new AndroidXmlPredefinedCodeStyle();
    xmlStyle.apply(settings);

    // Java:
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    // Set Import order
    javaSettings.IMPORT_LAYOUT_TABLE.copyFrom(getAndroidImportOrder());

    // Don't use wildcard imports
    javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(new PackageEntryTable());
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 99; // Class count to use import with "*"
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 99; // Name count to use static import with "*"

    // set margin to 100 columns
    settings.setDefaultRightMargin(100);
  }

  private static PackageEntryTable getAndroidImportOrder() {
    PackageEntryTable table = new PackageEntryTable();
    // From http://s.android.com/source/code-style.html, the order should be:
    //  1. Android imports
    //  2. Imports from 3rd parties (com, junit, net, org)
    //  3. java and javax
    table.addEntry(new PackageEntry(false, "android", true));
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    table.addEntry(new PackageEntry(false, "com", true));
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    table.addEntry(new PackageEntry(false, "junit", true));
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    table.addEntry(new PackageEntry(false, "net", true));
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    table.addEntry(new PackageEntry(false, "org", true));
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    table.addEntry(new PackageEntry(false, "java", true));
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    table.addEntry(new PackageEntry(false, "javax", true));
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    // handle any other conditions
    table.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    // location of static imports is not defined, we put it last
    table.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
    table.addEntry(PackageEntry.BLANK_LINE_ENTRY);

    return table;
  }
}
