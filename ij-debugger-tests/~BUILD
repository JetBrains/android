load(":ij-debugger-tests.bzl", "debugger_test", "include_tests_filter")

debugger_test(
    name = "k2-tests-jvm",
    expected_results = "k2-expected-results-jvm.txt",
    shard_count = 50,
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

debugger_test(
    name = "k2-tests-art",
    expected_results = "k2-expected-results-art.txt",
    run_on_art = True,
    shard_count = 50,
    tags = ["manual"],
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)

flaky_tests = [
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$MultipleBreakpoints$Uncategorized#testNestedCoroutineContexts",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$MultipleBreakpoints$Uncategorized#testOptimisedVariableEvaluation",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$RunToCursor#testRunToCursorSuspendSameJobContinuation",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$RunToCursor#testRunToCursorSuspendSameLambda",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepInto#testSmartStepIntoWithContextBasic",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepInto#testStepIntoSuspendLambda",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepOver$Uncategorized#testStepOverCoroutineScope",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepOver$Uncategorized#testStepOverRunBlockingInsideCoroutine",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepOver$Unsupported#testStopInCoroutineScope",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint$Uncategorized#testAnnotationValue",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK1CodeKotlinSteppingTestGenerated$Custom#testBreakpointOnLineWithLambdaStart",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK1CodeKotlinSteppingTestGenerated$Custom#testDefaultLambdaParameterInConstructor",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK1CodeKotlinSteppingTestGenerated$Custom#testSmartStepIntoWithOverrides",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK1CodeKotlinSteppingTestGenerated$StepOver$Uncategorized#testStopInSuspendFunctionWithoutSuspendPoints",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK1CodeKotlinVariablePrintingTestGenerated#testExtractCapturesFromSuspendLambdas",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$MultipleBreakpoints$Uncategorized#testNestedCoroutineContexts",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$MultipleBreakpoints$Uncategorized#testOptimisedVariableEvaluation",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$RunToCursor#testRunToCursorSuspendSameJobCompletionOnly",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$RunToCursor#testRunToCursorSuspendSameLambda",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepOver$Uncategorized#testMultipleCoroutines",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepOver$Uncategorized#testStepOverCoroutineScope",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepOver$Uncategorized#testStepOverLaunch",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepOver$Unsupported#testStopInCoroutineScope",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Uncategorized#testAnnotationValue",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinSteppingTestGenerated$Custom#testBreakpointOnLineWithLambdaStart",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinSteppingTestGenerated$StepOver$Uncategorized#testStopInSuspendFunctionWithoutSuspendPoints",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IndyLambdaKotlinSteppingTestGenerated$Custom#testBreakpointOnLineWithLambdaStart",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$RunToCursor#testRunToCursorSuspendSameJobContinuation",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepInto#testSmartStepIntoWithContextBasic",
    "org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint$Coroutines$StepInto#testSmartStepIntoInlineSuspendLambda",
]

debugger_test(
    name = "flaky",
    filter = include_tests_filter(flaky_tests),
    run_on_art = True,
    shard_count = 50,
    test_dep = "@tools_idea//plugins/kotlin/jvm-debugger/test/k2:kotlin-jvm-debugger-test_test_lib",
)
