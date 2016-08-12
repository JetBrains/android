/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.codeanalysis.datastructs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * A class provides static methods and constants to represent and
 * work with Java modifiers. A replica from soot.Modifier
 */
public class Modifier {
  public static final int ABSTRACT = 0x0400;
  public static final int FINAL = 0x0010;
  public static final int INTERFACE = 0x0200;
  public static final int NATIVE = 0x0100;
  public static final int PRIVATE = 0x0002;
  public static final int PROTECTED = 0x0004;
  public static final int PUBLIC = 0x0001;
  public static final int STATIC = 0x0008;
  public static final int SYNCHRONIZED = 0x0020;
  public static final int TRANSIENT = 0x0080; /* VARARGS for methods */
  public static final int VOLATILE = 0x0040; /* BRIDGE for methods */
  public static final int STRICTFP = 0x0800;
  public static final int ANNOTATION = 0x2000;
  public static final int ENUM = 0x4000;

  public static final int DEFAULT = 0x0002;


  private Modifier() {

  }

  public static boolean isAbstract(int m) {
    return (m & ABSTRACT) != 0;
  }

  public static boolean isFinal(int m) {
    return (m & FINAL) != 0;
  }

  public static boolean isInterface(int m) {
    return (m & INTERFACE) != 0;
  }

  public static boolean isNative(int m) {
    return (m & NATIVE) != 0;
  }

  public static boolean isPrivate(int m) {
    return (m & PRIVATE) != 0;
  }

  public static boolean isProtected(int m) {
    return (m & PROTECTED) != 0;
  }

  public static boolean isPublic(int m) {
    return (m & PUBLIC) != 0;
  }

  public static boolean isStatic(int m) {
    return (m & STATIC) != 0;
  }

  public static boolean isSynchronized(int m) {
    return (m & SYNCHRONIZED) != 0;
  }

  public static boolean isTransient(int m) {
    return (m & TRANSIENT) != 0;
  }

  public static boolean isVolatile(int m) {
    return (m & VOLATILE) != 0;
  }

  public static boolean isStrictFP(int m) {
    return (m & STRICTFP) != 0;
  }

  public static boolean isAnnotation(int m) {
    return (m & ANNOTATION) != 0;
  }

  public static boolean isEnum(int m) {
    return (m & ENUM) != 0;
  }

  public static String toString(int m) {
    StringBuffer buffer = new StringBuffer();

    if (isPublic(m)) {
      buffer.append("public ");
    }
    else if (isPrivate(m)) {
      buffer.append("private ");
    }
    else if (isProtected(m)) {
      buffer.append("protected ");
    }

    if (isAbstract(m)) {
      buffer.append("abstract ");
    }

    if (isStatic(m)) {
      buffer.append("static ");
    }

    if (isFinal(m)) {
      buffer.append("final ");
    }

    if (isSynchronized(m)) {
      buffer.append("synchronized ");
    }

    if (isNative(m)) {
      buffer.append("native ");
    }

    if (isTransient(m)) {
      buffer.append("transient ");
    }

    if (isVolatile(m)) {
      buffer.append("volatile ");
    }

    if (isStrictFP(m)) {
      buffer.append("strictfp ");
    }

    if (isAnnotation(m)) {
      buffer.append("annotation ");
    }

    if (isEnum(m)) {
      buffer.append("enum ");
    }

    if (isInterface(m)) {
      buffer.append("interface ");
    }

    return (buffer.toString()).trim();
  }

  public static int ParseModifierList(@NotNull PsiModifierList modList) {
    int modifierBits = Modifier.PRIVATE;
    if (modList != null) {
      for (PsiElement pe : modList.getChildren()) {
        if (pe instanceof PsiKeyword) {
          PsiKeyword curWord = (PsiKeyword)pe;
          if (curWord.textMatches("public")) {
            modifierBits &= ~0x3;
            modifierBits |= Modifier.PUBLIC;
          }
          else if (curWord.textMatches("private")) {
            modifierBits &= ~0x3;
            modifierBits |= Modifier.PRIVATE;
          }
          else if (curWord.textMatches("final")) {
            modifierBits |= Modifier.FINAL;
          }
          else if (curWord.textMatches("abstract")) {
            modifierBits |= Modifier.ABSTRACT;
          }
          else if (curWord.textMatches("interface")) {
            modifierBits |= Modifier.INTERFACE;
          }
          else if (curWord.textMatches("native")) {
            modifierBits |= Modifier.NATIVE;
          }
          else if (curWord.textMatches("protected")) {
            modifierBits &= ~0x3;
            modifierBits |= Modifier.PROTECTED;
          }
          else if (curWord.textMatches("static")) {
            modifierBits |= Modifier.STATIC;
          }
          else if (curWord.textMatches("synchronized")) {
            modifierBits |= Modifier.SYNCHRONIZED;
          }
          else if (curWord.textMatches("transient")) {
            modifierBits |= Modifier.TRANSIENT;
          }
          else if (curWord.textMatches("volatile")) {
            modifierBits |= Modifier.VOLATILE;
          }
          else if (curWord.textMatches("strictfp")) {
            modifierBits |= Modifier.STRICTFP;
          }
          else if (curWord.textMatches("annotation")) {
            modifierBits |= Modifier.ANNOTATION;
          }
          else if (curWord.textMatches("enum")) {
            modifierBits |= Modifier.ENUM;
          }
        }
      }
    }
    return modifierBits;
  }


}
