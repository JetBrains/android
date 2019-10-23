# Generate lexer and parser code

## Prerequisites

Install Intellij's Grammar-Kit plugin which comes with a forked version of JFlex.
* Preferences -> Plugins -> Browse Repositories.
* Search for Grammar-Kit.
* Restart IDE.

## Generate JFlex lexer file

WARNING: We have custom tokens! The act of generating JFlex lexer itself will not yield all tokens
required by databinding! It is important that you follow the instructions below.

db.bnf defines the grammar and most of the tokens for data-binding expressions used inside layout
files.

After edits, generate the lexer (only if tokens changed).
* We do this by right clicking on db.bnf and selecting Generate JFlex lexer.
* When prompted for name, use "_DbLexer.flex" so it overwrites the existing one.
* Open the flex file and re-add the custom tokens that only exist in
flex file.
  * Use "git diff" to make sure only changes you intended are in the flex file.

## Run JFlex generator

Right click on _DbLexer.flex and select Run JFlex Generator.

## Generate parser

Right click on db.bnf and select Generate Parser Code.