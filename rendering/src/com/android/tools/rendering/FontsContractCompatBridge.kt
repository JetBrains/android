/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering

import com.android.ide.common.fonts.FontDetail
import com.android.ide.common.fonts.FontProvider
import com.android.ide.common.fonts.NORMAL
import com.android.tools.fonts.DownloadableFontCacheService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.abs

/**
 * Bridge class used by [com.android.tools.rendering.classloading.FontsContractCompatTransform] to intercept downloadable font requests
 * (e.g. Compose GoogleFont) and resolve them via Studio's [DownloadableFontCacheService].
 */
object FontsContractCompatBridge {

  internal data class ResolvedFont(val singleRequest: Any, val fontFile: File, val bestMatch: FontDetail)

  /**
   * Intercepts `FontsContractCompat.fetchFonts(...)` calls and attempts to resolve the requested font via Studio's downloadable font cache.
   *
   * @param context the Android Context passed to `fetchFonts`
   * @param cancellationSignal optional CancellationSignal
   * @param fontRequest the `FontRequest` instance containing query parameters
   * @return a `FontsContractCompat.FontFamilyResult` instance if resolved, or null if resolution fails
   */
  @JvmStatic
  fun fetchFonts(context: Any?, cancellationSignal: Any?, fontRequest: Any?): Any? {
    try {
      val (singleRequest, fontFile, bestMatch) = resolveFontFile(fontRequest) ?: return null
      val classLoader = singleRequest.javaClass.classLoader ?: context?.javaClass?.classLoader ?: return null
      val isItalic = bestMatch.italics != NORMAL
      return createFontFamilyResult(classLoader, fontFile, bestMatch.weight, isItalic)
    } catch (e: Exception) {
      thisLogger().warn("Failed to intercept downloadable font request", e)
    }

    return null
  }

  /**
   * Intercepts `FontsContractCompat.requestFont(...)` calls and attempts to resolve the requested font via Studio's downloadable font
   * cache.
   *
   * @param context the Android Context passed to `requestFont`
   * @param fontRequest the `FontRequest` or `List<FontRequest>` instance containing query parameters
   * @param callback optional callback object (`FontRequestCallback` or `CallbackWrapper`)
   * @param style target typeface style
   * @return a `Typeface` instance if resolved, or null if resolution fails
   */
  @JvmStatic
  fun requestFont(context: Any?, fontRequest: Any?, callback: Any?, style: Int): Any? {
    try {
      val (singleRequest, fontFile, _) = resolveFontFile(fontRequest) ?: return null

      val typefaceClass =
        try {
          singleRequest.javaClass.classLoader?.loadClass("android.graphics.Typeface")
            ?: context?.javaClass?.classLoader?.loadClass("android.graphics.Typeface")
            ?: Class.forName("android.graphics.Typeface")
        } catch (_: Exception) {
          Class.forName("android.graphics.Typeface")
        }

      val createFromFileMethod = typefaceClass.getMethod("createFromFile", File::class.java)
      var typeface = createFromFileMethod.invoke(null, fontFile)

      if (typeface != null && style != 0) {
        try {
          val createMethod = typefaceClass.getMethod("create", typefaceClass, Int::class.javaPrimitiveType)
          val styledTypeface = createMethod.invoke(null, typeface, style)
          if (styledTypeface != null) {
            typeface = styledTypeface
          }
        } catch (e: Exception) {
          thisLogger().warn("Failed to create styled typeface", e)
        }
      }

      if (typeface != null && callback != null) {
        notifyCallback(callback, typeface)
      }

      return typeface
    } catch (e: Exception) {
      thisLogger().warn("Failed to intercept downloadable requestFont request", e)
    }

    return null
  }

  private fun resolveFontFile(fontRequest: Any?): ResolvedFont? {
    if (fontRequest == null) return null

    val singleRequest = if (fontRequest is List<*>) fontRequest.firstOrNull() else fontRequest
    if (singleRequest == null) return null

    val getQueryMethod = singleRequest.javaClass.getMethod("getQuery")
    val query = getQueryMethod.invoke(singleRequest) as? String ?: return null

    val (fontName, targetWeight, targetItalic) = parseQuery(query)

    val fontCacheService = ApplicationManager.getApplication()?.getService(DownloadableFontCacheService::class.java) ?: return null

    val provider = FontProvider.GOOGLE_PROVIDER
    val fontFamily = fontCacheService.findFont(provider, fontName) ?: return null

    val bestMatch = findBestMatchDetail(fontFamily.fonts, targetWeight, targetItalic) ?: fontFamily.fonts.firstOrNull() ?: return null

    var fontFile = fontCacheService.getCachedFontFile(bestMatch)
    if (fontFile == null || !fontFile.exists()) {
      try {
        val downloadFuture = fontCacheService.download(fontFamily)
        downloadFuture.get(2, TimeUnit.SECONDS)
        fontFile = fontCacheService.getCachedFontFile(bestMatch)
      } catch (e: TimeoutException) {
        thisLogger().warn("Timed out downloading font $fontName for preview", e)
      } catch (e: Exception) {
        thisLogger().warn("Error downloading font $fontName for preview", e)
      }
    }

    if (fontFile != null && fontFile.exists()) {
      return ResolvedFont(singleRequest, fontFile, bestMatch)
    }

    return null
  }

  private fun notifyCallback(callback: Any, typeface: Any) {
    try {
      var clazz: Class<*>? = callback.javaClass
      while (clazz != null && clazz != Any::class.java) {
        val methods = clazz.declaredMethods
        val method = methods.firstOrNull { it.name == "onTypefaceRetrieved" && it.parameterTypes.size == 1 }
        if (method != null) {
          method.isAccessible = true
          method.invoke(callback, typeface)
          return
        }
        clazz = clazz.superclass
      }
    } catch (e: Exception) {
      thisLogger().warn("Failed to invoke onTypefaceRetrieved on callback", e)
    }
  }

  internal fun parseQuery(query: String): Triple<String, Int, Boolean> {
    var fontName = query.replace("+", " ")
    var weight = 400
    var italic = false

    if (query.contains("name=")) {
      val params = query.split("&")
      for (param in params) {
        val keyValue = param.split("=", limit = 2)
        if (keyValue.size == 2) {
          val key = keyValue[0].trim()
          val value = keyValue[1].trim()
          when (key) {
            "name" -> fontName = value.replace("+", " ")
            "weight" -> weight = value.toIntOrNull() ?: 400
            "italic" -> italic = value == "1" || value.equals("true", ignoreCase = true)
          }
        }
      }
    }
    return Triple(fontName, weight, italic)
  }

  private fun findBestMatchDetail(details: List<FontDetail>, targetWeight: Int, targetItalic: Boolean): FontDetail? {
    return details.asSequence().filter { (it.italics != NORMAL) == targetItalic }.minByOrNull { abs(it.weight - targetWeight) }
      ?: details.minByOrNull { abs(it.weight - targetWeight) }
  }

  private fun createFontFamilyResult(classLoader: ClassLoader, fontFile: File, weight: Int, italic: Boolean): Any? {
    val fontInfoClass = classLoader.loadClass("androidx.core.provider.FontsContractCompat\$FontInfo")
    val fontFamilyResultClass = classLoader.loadClass("androidx.core.provider.FontsContractCompat\$FontFamilyResult")
    val uriClass = classLoader.loadClass("android.net.Uri")

    val fromFileMethod = uriClass.getMethod("fromFile", File::class.java)
    val uri = fromFileMethod.invoke(null, fontFile)

    val fontInfoConstructor =
      fontInfoClass.getConstructor(
        uriClass,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
      )
    val fontInfo = fontInfoConstructor.newInstance(uri, 0, weight, italic, 0 /* RESULT_CODE_OK */)

    val fontInfoArray = ReflectArray.newInstance(fontInfoClass, 1)
    ReflectArray.set(fontInfoArray, 0, fontInfo)

    val fontFamilyResultConstructor =
      fontFamilyResultClass.getDeclaredConstructor(Int::class.javaPrimitiveType, fontInfoArray.javaClass).apply { isAccessible = true }

    return fontFamilyResultConstructor.newInstance(0 /* STATUS_OK */, fontInfoArray)
  }
}
