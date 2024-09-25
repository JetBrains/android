"""Utility functions to expand make variables."""

def _is_valid_make_var(varname):
    """Check if the make variable name seems valid."""
    if len(varname) == 0:
        return False

    # According to gnu make, any chars not whitespace, ':', '#', '=' are valid.
    invalid_chars = ":#= \t\n\r"
    for n in range(0, len(invalid_chars)):
        if invalid_chars[n] in varname:
            return False
    return True

def expand_make_variables(attr_name, expression, ctx, additional_subs = {}):
    """Substitutes make variables defined in $() syntax.

    Because ctx.expand_make_variables is deprecated, we need to be able to do the
    substitution without relying on it.
    Before the aspect is processed, the build system already detects most/all of
    the failure modes and the aspect does not get processed, but including them
    here helps with following the logic.

    Args:
      attr_name: The attribute name. Used for error reporting.
      expression: The expression to expand. It can contain references to "Make
        variables".
      ctx: The context containing default make variables to subtitute.
      additional_subs: Additional substitutions to make beyond the default make
        variables.

    Returns:
      Returns a string after expanding all references to "Make variables". The
      variables must have the following format: $(VAR_NAME). Also, $$VAR_NAME
      expands to $VAR_NAME.


    """
    if "$" not in expression:
        return expression

    current_offset = 0
    rv = ""
    substitutions = {}
    substitutions.update(ctx.var)

    # make variables from ctx.var can be overridden
    substitutions.update(additional_subs)

    # skylark does not support while. This is the maximum iteration count this
    # loop will need, but it will exit early if possible.
    for _n in range(0, len(expression)):
        if current_offset >= len(expression):
            break
        begin_dollars = expression.find("$", current_offset)
        if begin_dollars == -1:
            # append whatever is left in expression
            rv = rv + expression[current_offset:]
            current_offset = len(expression)
            continue
        if begin_dollars != current_offset:
            rv = rv + expression[current_offset:begin_dollars]

        # consume the entire run of $$$...
        end_dollars = begin_dollars + 1
        for _m in range(end_dollars, len(expression)):
            if expression[end_dollars] == "$":
                end_dollars = end_dollars + 1
            else:
                break
        if (end_dollars - begin_dollars) % 2 == 0:
            # even number of '$'
            rv = rv + "$" * ((end_dollars - begin_dollars) // 2)
            current_offset = end_dollars
            continue

        # odd number of '$'
        if end_dollars == len(expression) or expression[end_dollars] != "(":
            # odd number of '$' at the end of the string is invalid
            # odd number of '$' followed by non-( is invalid
            fail("expand_make_variables: unterminated $", attr_name)
        end_parens = expression.find(")", end_dollars)
        if end_parens == -1:
            # no end parens is invalid
            fail("expand_make_variables: unterminated variable reference", attr_name)

        # odd number of '$', but integer division will provide correct count
        rv = rv + "$" * ((end_dollars - begin_dollars) // 2)
        varname = expression[end_dollars + 1:end_parens]
        if not _is_valid_make_var(varname):
            # invalid make variable name
            fail("expand_make_variables: $(%s) invalid name" % varname, attr_name)
        if not varname in substitutions:
            # undefined make variable
            fail("expand_make_variables: $(%s) not defined" % varname, attr_name)
        rv = rv + substitutions[varname]
        current_offset = end_parens + 1
    return rv
