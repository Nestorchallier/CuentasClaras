package com.nestor.cuentasclaras.capture

import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Prefs
import java.text.Normalizer
import java.time.LocalDate

/**
 * Entiende mensajes cortos como "café 2500", "super 15 mil tarjeta", "+ sueldo 800000" o "nafta 20000 ayer"
 * (los usa el bot de Telegram y los avisos del banco).
 */
object QuickText {
    data class Parsed(
        val type: String,
        val amount: Double,
        val category: Category?,
        val account: Account?,
        val note: String,
        val date: LocalDate
    )

    /** Palabras frecuentes → nombre de categoría (se busca la categoría que contenga ese nombre). */
    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "supermercado" to listOf("super", "supermercado", "chino", "coto", "carrefour", "dia", "jumbo", "disco", "vea", "almacen", "verduleria", "carniceria"),
        "comida" to listOf("comida", "almuerzo", "cena", "pizza", "empanadas", "resto", "restaurante", "hamburguesa", "burger", "sushi", "helado", "panaderia", "kiosco"),
        "cafe" to listOf("cafe", "cafecito", "medialunas"),
        "delivery" to listOf("delivery", "pedidosya", "rappi"),
        "transporte" to listOf("colectivo", "bondi", "sube", "subte", "tren", "uber", "cabify", "didi", "peaje", "estacionamiento", "transporte"),
        "taxi" to listOf("taxi", "remis"),
        "nafta" to listOf("nafta", "combustible", "ypf", "shell", "axion", "gnc"),
        "salud" to listOf("farmacia", "medico", "remedio", "remedios", "prepaga", "obra social", "dentista", "salud"),
        "servicios" to listOf("luz", "gas", "agua", "internet", "telefono", "celular", "edenor", "edesur", "metrogas", "abl", "servicios", "cable"),
        "vivienda" to listOf("alquiler", "expensas", "vivienda"),
        "salidas" to listOf("birra", "cerveza", "bar", "boliche", "cine", "salida", "teatro", "recital"),
        "suscripciones" to listOf("netflix", "spotify", "disney", "youtube", "hbo", "max", "prime", "icloud", "suscripcion"),
        "ropa" to listOf("ropa", "zapatillas", "remera", "pantalon", "campera"),
        "mascotas" to listOf("veterinaria", "veterinario", "perro", "gato", "balanceado", "mascota"),
        "regalos" to listOf("regalo", "regalos", "cumple"),
        "educacion" to listOf("curso", "libro", "libros", "colegio", "facultad", "educacion"),
        "gimnasio" to listOf("gym", "gimnasio"),
        "hogar" to listOf("ferreteria", "hogar", "limpieza"),
        "tarjeta" to listOf("tarjeta"),
        "sueldo" to listOf("sueldo", "salario", "aguinaldo"),
        "freelance" to listOf("freelance", "trabajo", "changa"),
        "ventas" to listOf("venta", "vendi")
    )

    /** Si no existe la categoría específica, se usa una más general. */
    private val FALLBACK = mapOf(
        "nafta" to "transporte", "taxi" to "transporte", "delivery" to "comida", "cafe" to "comida",
        "hogar" to "vivienda", "gimnasio" to "salud", "suscripciones" to "servicios", "freelance" to "extras", "ventas" to "extras"
    )

    private val INCOME_WORDS = listOf("ingreso", "cobre", "cobro", "sueldo", "salario", "aguinaldo", "me pagaron", "recibi", "vendi")

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun hasWord(text: String, word: String) = Regex("(^|[^a-z0-9])" + Regex.escape(word) + "($|[^a-z0-9])").containsMatchIn(text)

    /** Devuelve null si no encuentra un monto. */
    fun parse(raw: String, cats: List<Category>, accs: List<Account>, forcedType: String? = null): Parsed? {
        val text = raw.trim()
        val n = norm(text)

        // Monto: el primer número (acepta "15.000", "2500,50", "15k", "15 mil", "$ 4.500").
        // Se busca en el texto original en minúsculas para que las posiciones coincidan al armar la nota.
        val m = Regex("(\\d[\\d.,]*)\\s*(k|mil)?(?![a-z0-9])").find(text.lowercase()) ?: return null
        var amount = Fmt.parseOrNull(m.groupValues[1]) ?: return null
        if (m.groupValues[2].isNotEmpty()) amount *= 1000
        if (amount <= 0) return null

        val type = forcedType ?: when {
            text.startsWith("+") -> TxType.INGRESO
            text.startsWith("-") -> TxType.GASTO
            INCOME_WORDS.any { hasWord(n, it) } -> TxType.INGRESO
            else -> TxType.GASTO
        }

        val date = when {
            hasWord(n, "anteayer") || n.contains("antes de ayer") -> LocalDate.now().minusDays(2)
            hasWord(n, "ayer") -> LocalDate.now().minusDays(1)
            else -> LocalDate.now()
        }

        // Cuenta: por nombre, o por palabras clave (tarjeta, efectivo, dólares). Si no, la del sueldo.
        val account = accs.filter { norm(it.name).length >= 3 && n.contains(norm(it.name)) }.maxByOrNull { it.name.length }
            ?: (if (hasWord(n, "tarjeta") || hasWord(n, "credito")) accs.firstOrNull { it.isCard } else null)
            ?: (if (hasWord(n, "efectivo") || hasWord(n, "cash")) accs.firstOrNull { norm(it.name).contains("efectivo") } else null)
            ?: (if (hasWord(n, "usd") || hasWord(n, "dolares") || n.contains("us$")) accs.firstOrNull { it.isUsd } else null)
            ?: accs.firstOrNull { it.id == Prefs.mainAccountId }
            ?: accs.firstOrNull { !it.isCard }
            ?: accs.firstOrNull()

        // Categoría: primero por nombre exacto de una categoría; después por palabras frecuentes.
        val typeCats = cats.filter { it.type == type }
        val category = typeCats.filter { hasWord(n, norm(it.name)) }.maxByOrNull { it.name.length }
            ?: KEYWORDS.entries.firstNotNullOfOrNull { (catName, words) ->
                if (!words.any { hasWord(n, it) }) null
                else typeCats.firstOrNull { norm(it.name).contains(catName) }
                    ?: FALLBACK[catName]?.let { fb -> typeCats.firstOrNull { norm(it.name).contains(fb) } }
            }
            ?: typeCats.firstOrNull { norm(it.name) == if (type == TxType.GASTO) "otros" else "extras" }
            ?: typeCats.firstOrNull()

        // Nota: el texto sin el monto ni los signos, con la primera letra en mayúscula.
        val note = text.removeRange(m.range.first.coerceAtMost(text.length), m.range.last.plus(1).coerceAtMost(text.length))
            .replace(Regex("^[+\\-\\s]+"), "").replace(Regex("\\$"), "").replace(Regex("\\s+"), " ").trim()
            .replaceFirstChar { it.uppercase() }

        return Parsed(type, amount, category, account, note, date)
    }
}
