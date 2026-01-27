package com.bablabs.bringabrainlanguage

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform