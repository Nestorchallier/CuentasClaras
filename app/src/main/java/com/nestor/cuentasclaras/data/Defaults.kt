package com.nestor.cuentasclaras.data

object Defaults {
    val palette: List<Long> = listOf(
        0xFFA78BFA, 0xFF38BDF8, 0xFFF472B6, 0xFFFB923C, 0xFFFACC15, 0xFF34D399,
        0xFFF87171, 0xFF818CF8, 0xFF2DD4BF, 0xFFE879F9, 0xFF94A3B8, 0xFFA3E635
    )

    private fun g(name: String, emoji: String, i: Int) =
        Category(name = name, emoji = emoji, color = palette[i % palette.size], type = TxType.GASTO, position = i)

    private fun inc(name: String, emoji: String, color: Long, i: Int) =
        Category(name = name, emoji = emoji, color = color, type = TxType.INGRESO, position = i)

    val categories: List<Category> = listOf(
        g("Vivienda", "🏠", 0), g("Comida", "🍽️", 1), g("Supermercado", "🛒", 2),
        g("Transporte", "🚗", 3), g("Servicios", "🧾", 4), g("Suscripciones", "📅", 5),
        g("Tarjeta", "💳", 6), g("Salud", "❤️", 7), g("Salidas", "🍻", 8), g("Otros", "📦", 10),
        inc("Sueldo", "💰", 0xFF34D399, 0), inc("Comisiones", "💵", 0xFF2DD4BF, 1), inc("Extras", "✨", 0xFFFACC15, 2)
    )

    val accounts: List<Account> = listOf(
        Account(name = "Efectivo", emoji = "💵", position = 0),
        Account(name = "Banco", emoji = "🏦", position = 1)
    )

    /** "Adiciones rápidas" en la pantalla de categorías */
    val suggestions: List<Category> = listOf(
        g("Ropa", "👕", 1), g("Préstamo", "🏦", 7), g("Taxi", "🚕", 4), g("Transporte público", "🚆", 3),
        g("Mascotas", "🐶", 3), g("Regalos", "🎁", 2), g("Educación", "📚", 8), g("Gimnasio", "🏋️", 5),
        g("Viajes", "✈️", 1), g("Café", "☕", 3), g("Delivery", "🛵", 6), g("Nafta", "⛽", 9),
        g("Hijos", "🧸", 2), g("Hogar", "🛋️", 0),
        inc("Freelance", "💻", 0xFF38BDF8, 3), inc("Ventas", "🛍️", 0xFFF472B6, 4),
        inc("Reintegros", "↩️", 0xFF94A3B8, 5), inc("Intereses", "📈", 0xFFA3E635, 6)
    )

    val emojis = listOf(
        "🏠", "🍽️", "🛒", "🚗", "🧾", "📅", "💳", "❤️", "🍻", "👕", "🚕", "🚆", "🐶", "🎁",
        "📚", "🏋️", "✈️", "☕", "🛵", "⛽", "🧸", "🛋️", "📱", "💡", "🎮", "💊", "📦",
        "💰", "💵", "✨", "💻", "🛍️", "📈", "↩️"
    )

    val accountEmojis = listOf("💵", "🏦", "💳", "💰", "📱", "🐷", "🪙", "🏧")
}
