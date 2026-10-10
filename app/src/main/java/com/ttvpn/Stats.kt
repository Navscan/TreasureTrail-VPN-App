package com.ttvpn

object Stats {
    @Volatile var rx: Long = 0L
    @Volatile var tx: Long = 0L

    fun clear() {
        rx = 0L
        tx = 0L
    }
}
