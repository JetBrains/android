// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.android.debugger

import com.android.dx.cf.direct.DirectClassFile
import com.android.dx.cf.direct.StdAttributeFactory
import com.android.dx.dex.DexOptions
import com.android.dx.dex.cf.CfOptions
import com.android.dx.dex.cf.CfTranslator
import com.android.dx.dex.file.ClassDefItem
import com.android.dx.dex.file.DexFile
import com.intellij.ide.plugins.DynamicallyLoaded
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad
import java.lang.reflect.Modifier

@DynamicallyLoaded
class AndroidDexWrapper {
    // Used in AndroidOClassLoadingAdapter#dex
    fun dex(classes: Collection<ClassToLoad>): ByteArray? {
        val dexOptions = DexOptions()
        val cfOptions = CfOptions()

        val dexFile = DexFile(dexOptions)

        val methodWithContext = CfTranslator::class.java.declaredMethods
            .singleOrNull { it.name == "translate" && Modifier.isStatic(it.modifiers) && it.parameterCount == 6 }

        val dxContext = methodWithContext?.let { Class.forName("com.android.dx.command.dexer.DxContext").newInstance() }

        for ((_, relativeFileName, bytes) in classes) {
            val cf = DirectClassFile(bytes, relativeFileName, true)
            cf.setAttributeFactory(StdAttributeFactory.THE_ONE)

            val classDef = if (methodWithContext != null) {
                methodWithContext(
                    null,
                    dxContext,
                    cf,
                    bytes,
                    cfOptions,
                    dexOptions,
                    dexFile
                ) as ClassDefItem
            } else {
                CfTranslator.translate(cf, bytes, cfOptions, dexOptions, dexFile)
            }

            dexFile.add(classDef)
        }

        return dexFile.toDex(null, false)
    }
}
