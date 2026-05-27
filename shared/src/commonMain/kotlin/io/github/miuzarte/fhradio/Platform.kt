package io.github.miuzarte.fhradio

expect fun readFileTextOrNull(path: String): String?
expect fun fileExists(path: String): Boolean
expect fun joinPath(base: String, relative: String): String
expect val needVolumeSync: Boolean
