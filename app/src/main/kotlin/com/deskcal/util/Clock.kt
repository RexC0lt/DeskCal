package com.deskcal.util

fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
    }
}
