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
package com.android.tools.idea.navigator.nodes.ndk.includes.model;

import org.jetbrains.annotations.NotNull;

/**
Describes the classifications of some known packages.

 <pre>
Terminology used:
  - Package: A standalone native component like SDL and OpenSSL. These exist within a pre-existing PackageType. For example,
      Third Party Packages <- Package type
        SDL                <- Package
          SDL.h            <- Header file
  - Module: A piece of a larger framework where the pieces are all logical peers (like cocos UI and cocos Network).
  - Component: A piece of a larger framework where the pieces operate at different logical levels (like NDK STL and NDK native app glue).
  - Folders: A simple include where it isn't known that the folder is a package, module, or component
 </pre>
*/
public enum PackageType {
  // Holds packages defined in CDep package manager (http://github.com/google/cdep). For example,
  //   CDep Packages
  //     SDL
  //     OpenSSL
  CDepPackage("CDep Packages"),
  // These are support files for Cocos editor (see https://github.com/cocos2d/cocos2d-x)
  CocosEditorSupportModule("Cocos Editor Support Modules"),
  // These are third party packages that ship as part of Cocos. For example
  //   Cocos Third Party Packages
  //     SDL
  //     OpenSSL
  CocosThirdPartyPackage("Cocos Third Party Packages"),
  // These are modules that are part of Cocos framework. For example,
  //   Cocos Modules
  //     2d
  //     audio
  CocosFrameworkModule("Cocos Modules"),
  // These are components that are part of the NDK. For example,
  //   NDK Components
  //     Android Platform
  //     CPU Features
  NdkComponent("NDK Components"),
  // These are traditional include folders that don't match any other pattern.
  IncludeFolder("Include Folders"),
  // These are include folders that look like they match the traditional pattern of third_party/libname. For example,
  //   Third Party
  //     SDL
  //     OpenSSL
  ThirdParty("Third Party Packages");

  @NotNull
  public final String myDescription;

  PackageType(@NotNull String description) {
    myDescription = description;
  }
}
