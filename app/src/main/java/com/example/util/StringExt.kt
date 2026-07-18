package com.example.util

/**
 * Mengembalikan [default] kalau string ini null ATAU cuma whitespace/kosong ("").
 * Dipakai buat nutupin data lama yang usernamenya kesimpen sebagai "" (bukan null)
 * akibat bug validasi form profil sebelumnya, sekaligus jadi standar fallback
 * ke depannya biar konsisten di semua layar.
 */
fun String?.orDefault(default: String): String =
    if (this.isNullOrBlank()) default else this

/**
 * Ubah string kosong/whitespace jadi null, biar rantai `?:` fallback
 * (misal: username -> email -> "Anonim") tetap jalan normal walau
 * username-nya "" bukan null.
 */
fun String?.nullIfBlank(): String? =
    if (this.isNullOrBlank()) null else this
