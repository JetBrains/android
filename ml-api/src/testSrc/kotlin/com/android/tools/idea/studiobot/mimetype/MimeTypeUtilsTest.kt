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
package com.android.tools.idea.studiobot.mimetype

import com.android.tools.idea.studiobot.MimeType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MimeTypeUtilsTest : BasePlatformTestCase() {

  fun test_checkDisplayName() {
    assertEquals("Kotlin", MimeType.KOTLIN.displayName())
    assertEquals("Kotlin", MimeType("text/kotlin").displayName())
    assertEquals("Kotlin", MimeType("text/kotlin; charset=us-ascii").displayName())
    assertEquals("Gradle DSL", MimeType.GRADLE_KTS.displayName())
    assertEquals("XML", MimeType.XML.displayName())
    assertEquals("Manifest", MimeType.MANIFEST.displayName())
    assertEquals("Manifest", MimeType("text/xml; role=manifest").displayName())
    assertEquals("Manifest", MimeType("text/xml; role=manifest; charset=us-ascii").displayName())
    assertEquals("Version Catalog", MimeType.VERSION_CATALOG.displayName())
    assertEquals("Resource XML", MimeType.RESOURCE.displayName())
    assertEquals("Markdown", MimeType.MARKDOWN.displayName())
    assertEquals("Layout", MimeType("text/xml; role=resource; folderType=layout").displayName())
  }

  fun test_checkGetAttribute() {
    assertEquals(
      "bar",
      MimeType("text/some-language; attribute1=value1; foo=bar; attribute3=value3")
        .getAttribute("foo"),
    )
    assertEquals(
      "value1",
      MimeType("text/some-language; attribute1=value1; foo=bar; attribute3=value3")
        .getAttribute("attribute1"),
    )
    assertEquals(
      "value3",
      MimeType("text/some-language; attribute1=value1; foo=bar; attribute3=value3")
        .getAttribute("attribute3"),
    )
    assertEquals(
      null,
      MimeType("text/some-language; attribute1=value1; foo=bar; attribute3=value3")
        .getAttribute("attribute2"),
    )
    assertEquals(null, MimeType("text/some-language").getAttribute("attribute2"))
  }

  fun test_base_checkThatParametersAreDropped() {
    assertEquals(MimeType("text/plain"), MimeType("text/plain").base())
    assertEquals(MimeType("text/plain"), MimeType("text/plain; charset=us-ascii").base())
  }

  fun test_normalize_checkThatBaseIsReplaced() {
    assertEquals(MimeType("text/java"), MimeType("text/x-java-source").normalize())
    assertEquals(
      MimeType("text/java; role=gradle"),
      MimeType("text/x-java-source;role=gradle").normalize(),
    )
  }

  fun test_normalize_checkThatUnsupportedAttributesAreDropped() {
    assertEquals(MimeType("text/java"), MimeType("text/x-java-source;charset=us-ascii").normalize())
  }

  fun test_normalize_checkAlreadyNormalized() {
    assertEquals(MimeType("text/java"), MimeType("text/java").normalize())
    assertEquals(
      MimeType("text/kotlin; role=gradle"),
      MimeType("text/kotlin; role=gradle").normalize(),
    )
  }

  fun test_normalize_reorderAttributesAndStandardizeSpacing() {
    assertEquals(
      MimeType("text/xml; role=resource; rootTag=merge"),
      MimeType("text/xml;rootTag=merge;role = resource").normalize(),
    )
  }

  fun test_base_ensureAttributesRemoved() {
    assertEquals(MimeType("text/kotlin"), MimeType("text/kotlin").base())
    assertEquals(MimeType("text/kotlin"), MimeType("text/kotlin\n").base())
    assertEquals(MimeType("text/kotlin"), MimeType("text/kotlin; role=gradle").base())
    assertEquals(MimeType("text/plain"), MimeType("text/plain; charset=us-ascii").base())
    assertEquals(MimeType(""), MimeType("").base())
  }

  fun test_isGradle_forGroovyAndKotlin() {
    assertTrue(MimeType.GRADLE.isGradle())
    assertTrue(MimeType.GRADLE.base() == MimeType.GROOVY)

    assertTrue(MimeType.GRADLE_KTS.isGradle())
    assertTrue(MimeType.GRADLE_KTS.base() == MimeType.KOTLIN)

    assertFalse(MimeType.GROOVY.isGradle())
    assertFalse(MimeType.KOTLIN.isGradle())
  }

  fun test_editorLanguage_works_for_java() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Test")
    val language = myFixture.editor.getLanguage()
    assertEquals(MimeType.JAVA, language)
  }

  fun test_withAttribute() {
    assertEquals(
      MimeType("text/xml; root=merge; folderType=layout"),
      MimeType.XML.withAttribute("ignore", null)
        .withAttribute("root", "merge")
        .withAttribute("folderType", "layout"),
    )
  }

  fun test_isText() {
    assertTrue(MimeType.JAVA.isText())
    assertTrue(MimeType.KOTLIN.isText())
    assertTrue(MimeType.XML.isText())
    assertTrue(MimeType.RESOURCE.isText())
    assertTrue(MimeType.DART.isText())
    assertTrue(MimeType.SHELL.isText())

    assertFalse(MimeType.WEBP.isText())
  }

  fun test_isCompatibleWith() {
    assertFalse(MimeType.JAVA.isCompatibleWith(MimeType.KOTLIN))
    assertFalse(MimeType.KOTLIN.isCompatibleWith(MimeType.JAVA))

    assertTrue(MimeType.KOTLIN.isCompatibleWith(MimeType.KOTLIN))
    assertTrue(MimeType.GRADLE_KTS.isCompatibleWith(MimeType.KOTLIN))
    assertTrue(MimeType.KOTLIN.isCompatibleWith(MimeType.GRADLE_KTS))
    assertTrue(MimeType.GRADLE.isCompatibleWith(MimeType.GROOVY))
    assertTrue(MimeType.GROOVY.isCompatibleWith(MimeType.GRADLE))

    assertTrue(MimeType.XML.isCompatibleWith(MimeType.RESOURCE))
    assertTrue(MimeType.XML.isCompatibleWith(MimeType.MANIFEST))
    assertTrue(MimeType.MANIFEST.isCompatibleWith(MimeType.XML))
    assertTrue(MimeType.RESOURCE.isCompatibleWith(MimeType.XML))

    assertFalse(MimeType.RESOURCE.isCompatibleWith(MimeType.MANIFEST))
    assertFalse(MimeType.MANIFEST.isCompatibleWith(MimeType.RESOURCE))

    assertTrue(
      MimeType("text/xml; root=merge; folder=layout")
        .isCompatibleWith(MimeType("text/xml; root=LinearLayout; folder=layout"))
    )

    assertFalse(
      MimeType("text/xml; root=merge; folderType=layout")
        .isCompatibleWith(MimeType("text/xml; root=merge; folderType=values"))
    )
  }
}
