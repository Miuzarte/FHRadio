package io.github.miuzarte.fhradio

expect fun readFileTextOrNull(path: String): String?
expect fun fileExists(path: String): Boolean
