package com.mfix.autoprint

object EscPos {
    val INIT = byteArrayOf(0x1B, 0x40)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val LF = byteArrayOf(0x0A)
    val CUT = byteArrayOf(0x1D, 0x56, 0x00)

    fun text(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII) + LF
}