The [wff_expression.bnf](wff_expressions.bnf) file was generated in part thanks to Gemini using the
[watch face XSDs](../../../../../../../../../../../../../base/wear-wff-schema/resources/specification) as input.

From the `.bnf` file, you can generate the [_WFFExpressionLexer.flex](_WFFExpressionLexer.flex) file using the
[Grammar Kit](https://plugins.jetbrains.com/plugin/6606-grammar-kit) plugin. Once the plugin is installed, right-click the `.bnf` file and select
`Generate JFlex Lexer`. You can also generate the parser files by selecting `Generate Parser Code`.

Finally, to generate [_WFFExpressionLexer.java](../../../../../../../../../../gen/com/android/tools/idea/wear/dwf/dom/raw/expressions/_WFFExpressionLexer.java), you need to first download [JFlex](https://jflex.de/download.html).
Download the `.tar.gz` and extract it. Then, right-click the [_WFFExpressionLexer.flex](_WFFExpressionLexer.flex) file
and select `Run JFlex Generator`. You'll have to then select the extracted JFlex folder.