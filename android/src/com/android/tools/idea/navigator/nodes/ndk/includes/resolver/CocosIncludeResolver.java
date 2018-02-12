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
package com.android.tools.idea.navigator.nodes.ndk.includes.resolver;

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Resolve Cocos components.
 */
class CocosIncludeResolver extends IncludeResolver {
  @NotNull private final IncludeResolver[] myResolvers;

  public CocosIncludeResolver() {
    myResolvers = new IncludeResolver[]{
      PlainFolderRegularExpressionIncludeResolver.include("^.*/cocos2d[^//]*/external/$"),
      PlainFolderRegularExpressionIncludeResolver.include("^.*/cocos2d[^//]*/cocos/editor-support/$"),
      PlainFolderRegularExpressionIncludeResolver.include("^.*/cocos2d[^//]*/$"),
      PlainFolderRegularExpressionIncludeResolver.include("^.*/cocos2d[^//]*/cocos/$"),
      cocosEditorPackage(),
      cocosPackage(),
      cocosExternalPackage("^(.*/cocos2d.*?)(/external/(.*?)(/.*))$"),
      cocosExternalPackage("^(.*/cocos2d.*?)(/(.*?)(/.*))$")
    };
  }

  @NotNull
  static IncludeResolver cocosPackage() {
    return new IndexedRegularExpressionIncludeResolver(PackageType.CocosFrameworkModule,
                                                       "^(.*/cocos2d.*?)(/cocos/(.*?)(/.*))$");
  }

  @NotNull
  static IncludeResolver cocosEditorPackage() {
    return new IndexedRegularExpressionIncludeResolver(PackageType.CocosEditorSupportModule,
                                                       "^(.*/cocos2d.*?)(/cocos/editor-support/(.*?)(/.*))$");
  }

  @NotNull
  static IncludeResolver cocosExternalPackage(@NotNull String pattern) {
    return new IndexedRegularExpressionIncludeResolver(PackageType.CocosThirdPartyPackage, pattern);
  }

  @Override
  @Nullable
  public SimpleIncludeValue resolve(@NotNull File includeFolder) {
    for (IncludeResolver resolver : myResolvers) {
      SimpleIncludeValue classifiedIncludeExpression = resolver.resolve(includeFolder);
      if (classifiedIncludeExpression != null) {
        return classifiedIncludeExpression;
      }
    }
    return null;
  }
}
