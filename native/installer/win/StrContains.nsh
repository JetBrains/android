# ${StrContains} OUT STRING SUBSTRING
# A method which searches a target string for a substring (case insensitive),
# setting OUT to 1 if found, 0 otherwise.

Var strContains_string
Var strContains_substring
Var strContains_stringLen
Var strContains_substringLen
Var strContains_loopStartOffset
Var strContains_loopLastOffset
Var strContains_loopSubstring
Var strContains_returnValue

Function StrContains
  Pop $strContains_substring
  Pop $strContains_string
  StrCpy $strContains_returnValue 0

  StrLen $strContains_stringLen $strContains_string
  StrLen $strContains_substringLen $strContains_substring

  StrCpy $strContains_loopStartOffset 0
  IntOp $strContains_loopLastOffset $strContains_stringLen - $strContains_substringLen

  # If we're searching for "ing" in "string", we search for "ing" against
  # "str", "tri", "rin", and finally "ing"
  loop:
    IntCmp $strContains_loopStartOffset $strContains_loopLastOffset 0 0 done

    StrCpy $strContains_loopSubstring $strContains_string $strContains_substringLen $strContains_loopStartOffset
    StrCmp $strContains_loopSubstring $strContains_substring found

    IntOp $strContains_loopStartOffset $strContains_loopStartOffset + 1
    Goto loop

  found:
    StrCpy $strContains_returnValue 1

  done:
    Push $strContains_returnValue
FunctionEnd

!macro StrContains OUT STRING SUBSTRING
  Push `${STRING}`
  Push `${SUBSTRING}`
  Call StrContains
  Pop `${OUT}`
!macroend

!define StrContains '!insertmacro StrContains'

Var strContainsAnyOf_string
Var strContainsAnyOf_chars
Var strContainsAnyOf_charsLen
Var strContainsAnyOf_loopStartOffset
Var strContainsAnyOf_loopLastOffset
Var strContainsAnyOf_loopSubstring
Var strContainsAnyOf_returnValue

Function StrContainsAnyOf
  Pop $strContainsAnyOf_chars
  Pop $strContainsAnyOf_string
  StrCpy $strContainsAnyOf_returnValue 0

  StrLen $strContainsAnyOf_charsLen $strContainsAnyOf_chars

  StrCpy $strContainsAnyOf_loopStartOffset 0
  IntOp $strContainsAnyOf_loopLastOffset $strContainsAnyOf_charsLen - 1

  loop:
    IntCmp $strContainsAnyOf_loopStartOffset $strContainsAnyOf_loopLastOffset 0 0 done

    StrCpy $strContainsAnyOf_loopSubstring $strContainsAnyOf_chars 1 $strContainsAnyOf_loopStartOffset
    ${StrContains} $strContainsAnyOf_returnValue $strContainsAnyOf_string $strContainsAnyOf_loopSubstring
    ${If} $strContainsAnyOf_returnValue = 1
      Goto done
    ${EndIf}

    IntOp $strContainsAnyOf_loopStartOffset $strContainsAnyOf_loopStartOffset + 1
    Goto loop

  done:
    Push $strContainsAnyOf_returnValue
FunctionEnd

!macro StrContainsAnyOf OUT STRING CHARS
  Push `${STRING}`
  Push `${CHARS}`
  Call StrContainsAnyOf
  Pop `${OUT}`
!macroend

!define StrContainsAnyOf '!insertmacro StrContainsAnyOf'
