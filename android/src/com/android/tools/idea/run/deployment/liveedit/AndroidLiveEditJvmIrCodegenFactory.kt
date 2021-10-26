/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import org.jetbrains.kotlin.analyzer.hasJdkCapability
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContextImpl
import org.jetbrains.kotlin.backend.common.ir.BuiltinSymbolsBase
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.common.phaser.invokeToplevel
import org.jetbrains.kotlin.backend.jvm.ir.getKtFile
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmBackendExtension
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensionsImpl
import org.jetbrains.kotlin.backend.jvm.JvmNameProvider
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.backend.jvm.serialization.JvmIdSignatureDescriptor
import org.jetbrains.kotlin.codegen.CodegenFactory
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.konan.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.KlibModuleOrigin
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmIrLinker
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmManglerDesc
import org.jetbrains.kotlin.ir.builders.TranslationPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.descriptors.IrFunctionFactory
import org.jetbrains.kotlin.ir.linkage.IrProvider
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrConfiguration
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.psi2ir.generators.DeclarationStubGeneratorImpl
import org.jetbrains.kotlin.psi2ir.generators.generateTypicalIrProviderList
import org.jetbrains.kotlin.psi2ir.preprocessing.SourceDeclarationsPreprocessor
import org.jetbrains.kotlin.resolve.CleanableBindingContext

/**
 * This models after JvmIrCodegenFactory from 1.5.30 but with some workaround
 *
 * Workaround #1: JvmIrCodegenFactory has an incorrect check that assume symbols are unbounded even
 *                if the analysis found a definition.
 */
open class AndroidLiveEditJvmIrCodegenFactory (
  private val phaseConfig: PhaseConfig,
  private val externalMangler: JvmManglerDesc? = null,
  private val externalSymbolTable: SymbolTable? = null,
  private val jvmGeneratorExtensions: JvmGeneratorExtensionsImpl = JvmGeneratorExtensionsImpl()
) : CodegenFactory {
  data class JvmIrBackendInput(
    val state: GenerationState,
    val irModuleFragment: IrModuleFragment,
    val symbolTable: SymbolTable,
    val phaseConfig: PhaseConfig,
    val irProviders: List<IrProvider>,
    val extensions: JvmGeneratorExtensionsImpl,
    val backendExtension: JvmBackendExtension,
    val notifyCodegenStart: () -> Unit,
  )

  override fun generateModule(state: GenerationState, files: Collection<KtFile>) {
    val input = convertToIr(state, files)
    doGenerateFilesInternal(input)
  }

  @JvmOverloads
  fun convertToIr(state: GenerationState, files: Collection<KtFile>, ignoreErrors: Boolean = false): JvmIrBackendInput {
    val (mangler, symbolTable) =
      if (externalSymbolTable != null) externalMangler!! to externalSymbolTable
      else {
        val mangler = JvmManglerDesc(MainFunctionDetector(state.bindingContext, state.languageVersionSettings))
        val symbolTable = SymbolTable(JvmIdSignatureDescriptor(mangler), IrFactoryImpl, JvmNameProvider)
        mangler to symbolTable
      }
    val psi2ir = Psi2IrTranslator(state.languageVersionSettings, Psi2IrConfiguration(ignoreErrors))
    val messageLogger = state.configuration[IrMessageLogger.IR_MESSAGE_LOGGER] ?: IrMessageLogger.None
    val psi2irContext = psi2ir.createGeneratorContext(state.module, state.bindingContext, symbolTable, jvmGeneratorExtensions)
    val pluginExtensions = IrGenerationExtension.getInstances(state.project)
    val functionFactory = IrFunctionFactory(psi2irContext.irBuiltIns, symbolTable)
    psi2irContext.irBuiltIns.functionFactory = functionFactory

    val stubGenerator =
      DeclarationStubGeneratorImpl(psi2irContext.moduleDescriptor, symbolTable, state.languageVersionSettings, jvmGeneratorExtensions)
    val frontEndContext = object : TranslationPluginContext {
      override val moduleDescriptor: ModuleDescriptor
        get() = psi2irContext.moduleDescriptor
      override val symbolTable: ReferenceSymbolTable
        get() = symbolTable
      override val typeTranslator: TypeTranslator
        get() = psi2irContext.typeTranslator
      override val irBuiltIns: IrBuiltIns
        get() = psi2irContext.irBuiltIns
    }
    val irLinker = JvmIrLinker(
      psi2irContext.moduleDescriptor,
      messageLogger,
      psi2irContext.irBuiltIns,
      symbolTable,
      functionFactory,
      frontEndContext,
      stubGenerator,
      mangler
    )

    val pluginContext by lazy {
      psi2irContext.run {
        val symbols = BuiltinSymbolsBase(irBuiltIns, moduleDescriptor.builtIns, symbolTable.lazyWrapper)
        IrPluginContextImpl(
          moduleDescriptor,
          bindingContext,
          languageVersionSettings,
          symbolTable,
          typeTranslator,
          irBuiltIns,
          irLinker,
          messageLogger,
          symbols
        )
      }
    }

    SourceDeclarationsPreprocessor(psi2irContext).run(files)

    for (extension in pluginExtensions) {
      psi2ir.addPostprocessingStep { module ->
        val old = stubGenerator.unboundSymbolGeneration
        try {
          stubGenerator.unboundSymbolGeneration = true
          extension.generate(module, pluginContext)
        } finally {
          stubGenerator.unboundSymbolGeneration = old
        }
      }
    }

    val dependencies = psi2irContext.moduleDescriptor.collectAllDependencyModulesTransitively().map {
      val kotlinLibrary = (it.getCapability(KlibModuleOrigin.CAPABILITY) as? DeserializedKlibModuleOrigin)?.library
      if (it.hasJdkCapability) {
        // For IDE environment only, i.e. when compiling for debugger
        // Deserializer for built-ins module should exist because built-in types returned from SDK belong to that module,
        // but JDK's built-ins module might not be in current module's dependencies
        // We have to ensure that deserializer for built-ins module is created
        irLinker.deserializeIrModuleHeader(it.builtIns.builtInsModule, null)
      }
      irLinker.deserializeIrModuleHeader(it, kotlinLibrary)
    }

    // Workaround #1: We add the stubGenerator as part of the irProvider. This works for Live Edit
    //                because any references outside of the current must be available in the application's runtime already.
    // val irProviders = listOf(irLinker)
    val irProviders = listOf(irLinker, stubGenerator)


    val irModuleFragment = psi2ir.generateModuleFragment(psi2irContext, files, irProviders, pluginExtensions, expectDescriptorToSymbol = null)
    irLinker.postProcess()

    stubGenerator.unboundSymbolGeneration = true

    // We need to compile all files we reference in Klibs
    irModuleFragment.files.addAll(dependencies.flatMap { it.files })

    if (!state.configuration.getBoolean(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT)) {
      val originalBindingContext = state.originalFrontendBindingContext as? CleanableBindingContext
                                   ?: error("BindingContext should be cleanable in JVM IR to avoid leaking memory: ${state.originalFrontendBindingContext}")
      originalBindingContext.clear()
    }
    return JvmIrBackendInput(
      state,
      irModuleFragment,
      symbolTable,
      phaseConfig,
      irProviders,
      jvmGeneratorExtensions,
      JvmBackendExtension.Default,
    ) {}
  }

  private fun ModuleDescriptor.collectAllDependencyModulesTransitively(): List<ModuleDescriptor> {
    val result = LinkedHashSet<ModuleDescriptor>()
    fun collectImpl(descriptor: ModuleDescriptor) {
      val dependencies = descriptor.allDependencyModules
      dependencies.forEach { if (result.add(it)) collectImpl(it) }
    }
    collectImpl(this)
    return result.toList()
  }

  fun doGenerateFilesInternal(input: JvmIrBackendInput) {
    val (state, irModuleFragment, symbolTable, phaseConfig, irProviders, extensions, backendExtension, notifyCodegenStart) = input
    val context = JvmBackendContext(
      state, irModuleFragment.irBuiltins, irModuleFragment, symbolTable, phaseConfig, extensions, backendExtension,
      notifyCodegenStart
    )
    /* JvmBackendContext creates new unbound symbols, have to resolve them. */
    ExternalDependenciesGenerator(symbolTable, irProviders).generateUnboundSymbolsAsDependencies()

    context.state.factory.registerSourceFiles(irModuleFragment.files.map(IrFile::getKtFile))

    jvmPhases.invokeToplevel(phaseConfig, context, irModuleFragment)

    // TODO: split classes into groups connected by inline calls; call this after every group
    //       and clear `JvmBackendContext.classCodegens`
    state.afterIndependentPart()
  }

  fun generateModuleInFrontendIRMode(
    state: GenerationState,
    irModuleFragment: IrModuleFragment,
    symbolTable: SymbolTable,
    extensions: JvmGeneratorExtensionsImpl,
    backendExtension: JvmBackendExtension,
    notifyCodegenStart: () -> Unit = {}
  ) {
    val irProviders = configureBuiltInsAndGenerateIrProvidersInFrontendIRMode(irModuleFragment, symbolTable, extensions)
    doGenerateFilesInternal(
      JvmIrBackendInput(
        state, irModuleFragment, symbolTable, phaseConfig, irProviders, extensions, backendExtension, notifyCodegenStart
      )
    )
  }

  fun configureBuiltInsAndGenerateIrProvidersInFrontendIRMode(
    irModuleFragment: IrModuleFragment,
    symbolTable: SymbolTable,
    extensions: JvmGeneratorExtensionsImpl,
  ): List<IrProvider> {
    irModuleFragment.irBuiltins.functionFactory = IrFunctionFactory(irModuleFragment.irBuiltins, symbolTable)
    return generateTypicalIrProviderList(
      irModuleFragment.descriptor, irModuleFragment.irBuiltins, symbolTable, extensions = extensions
    )
  }
}
