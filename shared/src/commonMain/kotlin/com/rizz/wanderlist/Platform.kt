package com.rizz.wanderlist

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform