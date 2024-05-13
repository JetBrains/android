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
package com.android.tools.idea.studiobot

/**
 * Represents the language and dialect of a source snippet, as an RFC 2046 mime type.
 *
 * For example, a Kotlin source file may have the mime type `text/kotlin`. However, if it
 * corresponds to a `build.gradle.kts` file, we'll also attach the mime parameter `role=gradle`,
 * resulting in mime type `text/kotlin; role=gradle`.
 *
 * For XML resource files, we'll attach other attributes; for example `role=manifest` for Android
 * manifest files and `role=resource` for XML resource files. For the latter we may also attach for
 * example `folderType=values`, and for XML files in general, the root tag, such as `text/xml;
 * role=resource; folderType=layout; rootTag=LinearLayout`.
 *
 * This class does not implement *all* aspects of the RFC; in particular, we don't treat attributes
 * as case-insensitive, and we only support value tokens, not value strings -- neither of these are
 * needed for our purposes.
 *
 * This is implemented using a value class, such that behind the scenes we're really just passing a
 * String around.
 */
@JvmInline
value class MimeType(val mimeType: String) {
  override fun toString() = mimeType

  companion object {
    // Well known mime types for major languages.

    /**
     * Well known name for Kotlin source snippets. This is the base mime type; consider using
     * [isKotlin] instead to check if a mime type represents Kotlin code such that it also picks up
     * `build.gradle.kts` files (which carry extra attributes in the mime type; see [GRADLE_KTS].)
     */
    val KOTLIN = MimeType("text/kotlin")

    /** Well known name for Java source snippets. */
    val JAVA = MimeType("text/java")

    /** Well known mime type for text files. */
    val TEXT = MimeType("text/plain")

    /**
     * Special marker mimetype for unknown or unspecified mime types. These will generally be
     * treated as [TEXT] for editor purposes. (The standard "unknown" mime type is
     * application/octet-stream (from RFC 2046) but we know this isn't binary data; it's text.)
     *
     * Note that [MimeType] is generally nullable in places where it's optional instead of being set
     * to this value, but this mime type is there for places where we need a specific value to point
     * to.
     */
    val UNKNOWN = MimeType("text/unknown")

    /**
     * Well known name for XML source snippets. This is the base mime type; consider using [isXml]
     * instead to check if a mime type represents any XML such that it also picks up manifest files,
     * resource files etc., which all carry extra attributes in the mime type; see for example
     * [MANIFEST] and [RESOURCE].
     */
    val XML = MimeType("text/xml")
    val PROPERTIES = MimeType("text/properties")
    val TOML = MimeType("text/toml")
    val JSON = MimeType("text/json")
    val REGEX = MimeType("text/x-regex-source")
    val GROOVY = MimeType("text/groovy")
    val C = MimeType("text/c")
    val CPP = MimeType("text/c++")
    val SVG = MimeType("image/svg+xml")
    val AIDL = MimeType("text/x-aidl-source")
    val PROTO = MimeType("text/x-protobuf")
    val SQL = MimeType("text/x-sql")
    val PROGUARD = MimeType("text/x-proguard")
    val PYTHON = MimeType("text/python")
    val JAVASCRIPT = MimeType("text/javascript")
    val TYPESCRIPT = MimeType("text/typescript")
    val DART = MimeType("application/dart")
    val RUST = MimeType("text/rust")
    val AGSL = MimeType("text/x-agsl")
    val SHELL = MimeType("application/x-sh")
    val YAML = MimeType("text/yaml")
    val GO = MimeType("text/go")
    val SCALA = MimeType("text/x-scala")
    val R = MimeType("text/x-r") // or rscript
    val RUBY = MimeType("text/ruby")
    val PHP = MimeType("text/php")
    val MATLAB = MimeType("text/matlab")
    val LUA = MimeType("text/lua")
    val JPEG = MimeType("image/jpeg")
    val PNG = MimeType("image/png")
    val HEIC = MimeType("image/heic")
    val HEIF = MimeType("image/heif")
    val WEBP = MimeType("image/webp")
    val MARKDOWN = MimeType("text/markdown")
    val CSHARP = MimeType("text/x-csharp")
    val BATCH = MimeType("application/x-batch")
    val CLOJURE = MimeType("text/x-clojure")
    val COFFEESCRIPT = MimeType("text/coffeescript")
    val CSS = MimeType("text/css")
    val FSHARP = MimeType("text/x-fsharp")
    val HANDLEBARS = MimeType("text/x-handlebars-template")
    val HAML = MimeType("text/x-haml")
    val HTML = MimeType("text/html")
    val INI = MimeType("text/x-ini")
    val TEX = MimeType("application/x-tex")
    val LESS = MimeType("text/less")
    val MAKEFILE = MimeType("text/x-makefile")
    val POWERSHELL = MimeType("application/x-powershell")
    val JADE = MimeType("text/jade")
    val SLIM = MimeType("text/x-slim")
    val SCSS = MimeType("text/scss")
    val SASS = MimeType("text/sass")
    val STYLUS = MimeType("text/stylus")
    val TSX = MimeType("text/tsx")
    val XSL = MimeType("application/xml")
    val TERRAFORM = MimeType("text/x-ruby")
    /**
     * Attribute used to indicate the role this source file plays; for example, an XML file may be a
     * "manifest" or a "resource".
     */
    const val ATTR_ROLE = "role"

    /** For XML resource files, the folder type if any (such as "values" or "layout") */
    const val ATTR_FOLDER_TYPE = "folderType"

    /** For XML files, the root tag in the content */
    const val ATTR_ROOT_TAG = "rootTag"
    const val VALUE_RESOURCE = "resource"
    const val VALUE_MANIFEST = "manifest"

    /**
     * Note that most resource files will also have a folder type, so don't use equality on this
     * mime type
     */
    val RESOURCE = MimeType("$XML; $ATTR_ROLE=resource")
    val MANIFEST = MimeType("$XML; $ATTR_ROLE=manifest; $ATTR_ROOT_TAG=manifest")
    val GRADLE = MimeType("$GROOVY; $ATTR_ROLE=gradle")
    val GRADLE_KTS = MimeType("$KOTLIN; $ATTR_ROLE=gradle")
    val VERSION_CATALOG = MimeType("$TOML; $ATTR_ROLE=version-catalog")
  }
}
