/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.testFramework.TestDataPath;

@SuppressWarnings("all")
@TestDataPath("$PROJECT_ROOT")
public class AndroidLayoutRenameTestGenerated extends AbstractAndroidLayoutRenameTest {

    public void testSimple() throws Exception {
        // Renaming synthetic elements is no longer supported when renaming resources.
        if (StudioFlags.RESOLVE_USING_REPOS.get()) { return; }
        String fileName = KotlinTestUtils.navigationMetadata("android-extensions-idea/testData/android/renameLayout/simple/");
        doTest(fileName);
    }
}
