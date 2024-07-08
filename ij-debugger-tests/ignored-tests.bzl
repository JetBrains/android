""" The list of test suites that are currently not passing on JVM """

TEST_EXCLUDE_FILTER_JVM = [
    # FAIL :
    "org.jetbrains.kotlin.idea.debugger.test.CoroutineDumpTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.ContinuationStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeContinuationStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.FileRankingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeXCoroutinesStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.Custom",
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinSteppingTestGenerated.Custom",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeFileRankingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeCoroutineDumpTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.XCoroutinesStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IndyLambdaIrKotlinSteppingTestGenerated.Custom",
    "org.jetbrains.kotlin.idea.debugger.test.IndyLambdaIrKotlinSteppingTestGenerated.StepOver.Uncategorized",

    # FLAKY:
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinSteppingTestGenerated.StepOver.Uncategorized",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOver.Uncategorized",
]

TEST_EXCLUDE_FILTER_ART = [

    # PARTIAL:
    # "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepInto",
    # "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated",

    # FAIL:
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.Custom",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.Filters",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.SmartStepInto",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepIntoOnly",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOut",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOver",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepInto#testObjectFun",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepInto#testClassObjectFunFromTopLevel",

    # UNKNOWN:
    "org.jetbrains.kotlin.idea.debugger.test.AsyncStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.BreakpointApplicabilityTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.ClassNameCalculatorTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.ContinuationStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.CoroutineDumpTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.FileRankingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IndyLambdaIrKotlinEvaluateExpressionTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IndyLambdaIrKotlinSteppingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IrBreakpointHighlightingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinEvaluateExpressionInMppTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinEvaluateExpressionTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinEvaluateExpressionWithIRFragmentCompilerTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinScriptEvaluateExpressionTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinSteppingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeAsyncStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeBreakpointHighlightingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeContinuationStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeCoroutineDumpTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeFileRankingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionInMppTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinVariablePrintingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeScriptEvaluateExpressionTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeXCoroutinesStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.KotlinExceptionFilterTest",
    "org.jetbrains.kotlin.idea.debugger.test.KotlinExceptionFilterTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.KotlinVariablePrintingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.PositionManagerTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.SelectExpressionForDebuggerTestWithAnalysisApiGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.SelectExpressionForDebuggerTestWithLegacyImplementationGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.dsl.KotlinDslTest",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceTestCaseGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.psi.collection.PositiveCollectionBuildTest",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.psi.collection.TypedCollectionChainTest",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.psi.java.AmbiguousChainsTest",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.psi.java.LocationPositiveChainTest",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.psi.java.NegativeJavaStreamTest",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.psi.java.TypedJavaChainTest",
    "org.jetbrains.kotlin.idea.debugger.test.sequence.psi.sequence.TypedSequenceChain",
    "org.jetbrains.kotlin.idea.debugger.test.SmartStepIntoTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.XCoroutinesStackTraceTestGenerated",
]
