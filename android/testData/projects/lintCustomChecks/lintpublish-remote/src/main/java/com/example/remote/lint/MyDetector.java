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

package com.example.remote.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class MyDetector extends Detector implements SourceCodeScanner {
    public static final Issue ISSUE =
            Issue.create(
                            "UnitTestLintCheck3",
                            "Custom Lint Check",
                            "This app should not implement java.util.Set.",
                            Category.CORRECTNESS,
                            8,
                            Severity.ERROR,
                            new Implementation(MyDetector.class, Scope.JAVA_FILE_SCOPE))
                    .
                    // Make sure other integration tests don't pick this up.
                    // The unit test will turn it on with android.lintOptions.check <id>
                    setEnabledByDefault(false);

    public MyDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("java.util.Set");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        context.report(
                ISSUE,
                declaration,
                context.getLocation((UElement) declaration),
                "Do not implement java.util.Set directly");
    }
}
