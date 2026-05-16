package io.github.miuzarte.fhradio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform