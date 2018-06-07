/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.lang.proguard;

import com.android.tools.idea.lang.proguard.psi.ProguardTypes;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Completion contributor for ProGuard tokens.
 */
public class ProguardCompletionContributor extends CompletionContributor {

  // A list of supported ProGuard flags obtained by naively running the following command in the ProGuard source tree:
  // cat ./src/proguard/ConfigurationConstants.java | grep -- "\"-[a-zA-Z0-9]" | perl -pe 's/.* \"-/\"/' | perl -pe 's/;.*/,/'
  private static final String[] VALID_FLAGS = new String[] {
    "adaptclassstrings",
    "adaptresourcefilecontents",
    "adaptresourcefilenames",
    "allowaccessmodification",
    "applymapping",
    "assumenosideeffects",
    "basedirectory",
    "classobfuscationdictionary",
    "defaultpackage",
    "dontnote",
    "dontobfuscate",
    "dontoptimize",
    "dontpreverify",
    "dontshrink",
    "dontskipnonpubliclibraryclasses",
    "dontskipnonpubliclibraryclassmembers",
    "dontusemixedcaseclassnames",
    "dontwarn",
    "dump",
    "flattenpackagehierarchy",
    "forceprocessing",
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
    "mergeinterfacesaggressively",
    "microedition",
    "obfuscationdictionary",
    "optimizationpasses",
    "optimizations",
    "outjars",
    "overloadaggressively",
    "packageobfuscationdictionary",
    "printconfiguration",
    "printmapping",
    "printseeds",
    "printusage",
    "renamesourcefileattribute",
    "repackageclasses",
    "resourcejars",
    "skipnonpubliclibraryclasses",
    "target",
    "useuniqueclassmembernames",
    "verbose",
    "whyareyoukeeping"
  };

  public ProguardCompletionContributor() {
    // Add basic autocompletion for "flag names".
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(ProguardTypes.FLAG_NAME).withLanguage(ProguardLanguage.INSTANCE),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext processingContext,
                                           @NotNull CompletionResultSet resultSet) {
               for (String flag : VALID_FLAGS) {
                 resultSet.addElement(LookupElementBuilder.create(flag));
               }
             }
           }
    );
  }
}
