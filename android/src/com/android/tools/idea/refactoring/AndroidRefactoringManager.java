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

package com.android.tools.idea.refactoring;

import com.android.tools.idea.refactoring.rtl.RtlSupportManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

public class AndroidRefactoringManager {
    private final Project myProject;
    private RtlSupportManager myRtlManager;

    public static AndroidRefactoringManager getInstance(Project project) {
        return ServiceManager.getService(project, AndroidRefactoringManager.class);
    }

    public AndroidRefactoringManager(Project project) {
        myProject = project;
    }

    public RtlSupportManager getRtlSupportManager() {
        if (myRtlManager == null) {
            myRtlManager = new RtlSupportManager(myProject);
        }
        return myRtlManager;
    }
}
