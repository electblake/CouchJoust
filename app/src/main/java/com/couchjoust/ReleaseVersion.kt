package com.couchjoust

/** SemVer precedence; build metadata does not affect update ordering. */
data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long, val prerelease: List<String>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (core != 0) return core
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val a = prerelease[i]; val b = other.prerelease[i]
            val aNumber = a.all { it.isDigit() }; val bNumber = b.all { it.isDigit() }
            val result = when {
                aNumber && bNumber -> a.toBigInteger().compareTo(b.toBigInteger())
                aNumber -> -1
                bNumber -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        fun parse(value: String): ReleaseVersion {
            val parts = value.removePrefix("v").substringBefore('+').split('-', limit = 2)
            val core = parts[0].split('.').map { it.toLong() }
            return ReleaseVersion(core[0], core[1], core[2], if (parts.size == 2) parts[1].split('.') else emptyList())
        }
    }
}
