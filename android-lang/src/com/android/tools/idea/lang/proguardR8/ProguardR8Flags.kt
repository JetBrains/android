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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.projectsystem.CodeShrinker

private val COMMON_FLAGS = setOf(
  "adaptclassstrings",
  "adaptresourcefilecontents",
  "adaptresourcefilenames",
  "allowaccessmodification",
  "applymapping",
  "assumenosideeffects",
  "basedirectory",
  "classobfuscationdictionary",
  "dontnote",
  "dontobfuscate",
  "dontoptimize",
  "dontshrink",
  "dontusemixedcaseclassnames",
  "dontwarn",
  "flattenpackagehierarchy",
  "ignorewarnings",
  "include",
  "injars",
  "keep",
  "keepattributes",
  "keepclasseswithmembernames",
  "keepclasseswithmembers",
  "keepclassmembernames",
  "keepclassmembers",
  "keepdirectories",
  "keepnames",
  "keeppackagenames",
  "keepparameternames",
  "libraryjars",
  "obfuscationdictionary",
  "optimizationpasses",
  "overloadaggressively",
  "packageobfuscationdictionary",
  "printconfiguration",
  "printmapping",
  "printseeds",
  "printusage",
  "renamesourcefileattribute",
  "repackageclasses",
  "verbose",
  "whyareyoukeeping",
  "if",
  "addconfigurationdebugging",
  "assumevalues",
  "optimizations"
)

val R8_FLAGS = COMMON_FLAGS + setOf(
  "alwaysinline",
  "checkdiscard",
  "identifiernamestring",
  "shrinkunusedprotofields",
)

val PROGUARD_FLAGS = COMMON_FLAGS + setOf(
  "assumenoexternalsideeffects",
  "assumenoescapingparameters",
  "assumenoexternalreturnvalues",
  "defaultpackage",
  "dontpreverify",
  "dontskipnonpubliclibraryclasses",
  "dontskipnonpubliclibraryclassmembers",
  "dump",
  "forceprocessing",
  "mergeinterfacesaggressively",
  "microedition",
  "outjars",
  "skipnonpubliclibraryclasses",
  "target",
  "useuniqueclassmembernames"
)

// Parameters after -keepattributes flag
internal val ATTRIBUTES = setOf(
  "AnnotationDefault",
  "Deprecated",
  "EnclosingMethod",
  "Exceptions",
  "InnerClasses",
  "LineNumberTable",
  "LocalVariableTable",
  "LocalVariableTypeTable",
  "MethodParameters",
  "RuntimeInvisibleAnnotations",
  "RuntimeInvisibleParameterAnnotations",
  "RuntimeInvisibleTypeAnnotations",
  "RuntimeVisibleAnnotations",
  "RuntimeVisibleParameterAnnotations",
  "RuntimeVisibleTypeAnnotations",
  "Signature",
  "SourceDebugExtension",
  "SourceDir",
  "SourceFile",
  "Synthetic"
)

/**
 * Returns supported flags for given code shrinker or default value - PROGUARD_FLAGS - if shrinker is null.
 */
fun getShrinkerFlagSet(codeShrinker: CodeShrinker?): Set<String> = when (codeShrinker) {
  CodeShrinker.R8 -> R8_FLAGS
  CodeShrinker.PROGUARD -> PROGUARD_FLAGS
  null -> PROGUARD_FLAGS
}
