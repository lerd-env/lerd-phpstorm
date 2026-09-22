package sh.lerd.ide.php

/** lerd names an installed PHP; PhpStorm names a language level per minor. */
object PhpVersions {
    private val VERSION = Regex("""(\d+)\.(\d+)""")

    fun languageLevel(version: String?): String? =
        version?.let { VERSION.find(it) }?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
}
