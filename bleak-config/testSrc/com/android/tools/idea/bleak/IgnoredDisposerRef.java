/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.bleak;

public class IgnoredDisposerRef implements IgnoreListEntry<DisposerLeakInfo> {

  private final String parentClass;
  private final String childClass;
  private final int maxCount;

  public IgnoredDisposerRef(String parent, String child, int maxCount) {
    this.parentClass = parent;
    this.childClass = child;
    this.maxCount = maxCount;
  }

  private static String stripLambdaIdentifier(String className) {
    return className.replaceFirst("\\$\\$Lambda\\$.*", "$$Lambda");
  }

  @Override
  public boolean test(DisposerLeakInfo dli) {
    return stripLambdaIdentifier(dli.getKey().getDisposable().getClass().getName()).equals(parentClass) &&
           stripLambdaIdentifier(dli.getKey().getKlass().getName()).equals(childClass) &&
           dli.getCount() <= maxCount;
  }
}