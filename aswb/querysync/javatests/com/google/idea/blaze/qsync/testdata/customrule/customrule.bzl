def _customrule_impl(ctx):
    return []

customrule = rule(
    implementation = _customrule_impl,
    attrs = {"deps": attr.string_list()},
)
