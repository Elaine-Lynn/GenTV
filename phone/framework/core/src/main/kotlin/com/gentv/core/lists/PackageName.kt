package com.gentv.core.lists

/**
 * Package names end up inside shell commands on the TV, so nothing reaches a command unless it
 * passes this check.
 */
object PackageName {
    private val PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun isValid(name: String): Boolean = PATTERN.matches(name)

    fun require(name: String): String {
        require(isValid(name)) { "not a package name: ${name.take(80)}" }
        return name
    }
}
