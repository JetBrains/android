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
package com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8TestCase
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassMember
import com.android.tools.idea.lang.proguardR8.psi.isConstructor
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.util.parentOfType

class ProguardR8ClassMemberTest : ProguardR8TestCase() {

  fun testIsConstructor() {
    myFixture.addClass(
      //language=JAVA
      """
        package p1.p2;
        class MyClass {}
      """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class p1.p2.MyClass {
          MyClass();
          p1.p2.MyClass();
          NotMyClass();
          p1.p2.NotMyClass();
          p3.MyClass();
        }
      """.trimIndent()
    )

    myFixture.moveCaret("My|Class()")
    var member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMember::class)!!
    assertThat(member.isConstructor()).isTrue()

    myFixture.moveCaret("p1.p2.My|Class()")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMember::class)!!
    assertThat(member.isConstructor()).isTrue()

    myFixture.moveCaret("NotMy|Class")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMember::class)!!
    assertThat(member.isConstructor()).isFalse()

    myFixture.moveCaret("p1.p2.NotMy|Class")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMember::class)!!
    assertThat(member.isConstructor()).isFalse()

    myFixture.moveCaret("p3.My|Class()")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMember::class)!!
    assertThat(member.isConstructor()).isFalse()
  }
}