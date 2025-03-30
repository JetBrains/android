/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.lint.checks.ViewTypeDetector;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.res.LocalResourceRepository;
import java.util.Collection;
import java.util.Collections;

public class LintIdeViewTypeDetector extends ViewTypeDetector {
  static final Implementation IMPLEMENTATION = new Implementation(
    LintIdeViewTypeDetector.class,
    Scope.JAVA_FILE_SCOPE);

  @Nullable
  @Override
  protected Collection<String> getViewTags(@NonNull Context context, @NonNull ResourceItem item) {
    ResourceRepository projectResources = context.getClient().getResourceRepository(context.getMainProject(), true, false);
    assert projectResources instanceof LocalResourceRepository : projectResources;
    String viewTag = IdeResourcesUtil.getViewTag(item);
    if (viewTag != null) {
      return Collections.singleton(viewTag);
    }

    return super.getViewTags(context, item);
  }
}
