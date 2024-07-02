""" The list of test suites that are currently not passing on JVM """

IGNORED_SUITES = [
    # FAIL :
    "org.jetbrains.kotlin.idea.debugger.test.CoroutineDumpTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.ContinuationStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeContinuationStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.FileRankingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeXCoroutinesStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated$Custom",
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinSteppingTestGenerated$Custom",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeFileRankingTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeCoroutineDumpTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.XCoroutinesStackTraceTestGenerated",
    "org.jetbrains.kotlin.idea.debugger.test.IndyLambdaIrKotlinSteppingTestGenerated$Custom",
    "org.jetbrains.kotlin.idea.debugger.test.IndyLambdaIrKotlinSteppingTestGenerated$StepOver$Uncategorized",

    # FLAKY:
    "org.jetbrains.kotlin.idea.debugger.test.IrKotlinSteppingTestGenerated$StepOver$Uncategorized",
    "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated$StepOver$Uncategorized",
]
