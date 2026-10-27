package com.claudia.controltv

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlin.math.max

/** Señal ya convertida a pulsos: frecuencia de la portadora y duraciones en microsegundos (marca, espacio, marca...). */
class Enc(val freq: Int, val pattern: IntArray)

/** Acumula pulsos alternando marca/espacio y une los tramos del mismo tipo. Ignora espacios al inicio. */
private class Pulses {
    private val items = ArrayList<Int>()
    private var lastMark = false

    fun mark(us: Int) = add(true, us)
    fun space(us: Int) = add(false, us)

    private fun add(isMark: Boolean, us: Int) {
        if (items.isEmpty()) {
            if (!isMark) return
            items.add(us)
            lastMark = true
            return
        }
        if (isMark == lastMark) {
            items[items.size - 1] = items[items.size - 1] + us
        } else {
            items.add(us)
            lastMark = isMark
        }
    }

    fun total(): Int = items.sum()
    fun toArray(): IntArray = items.toIntArray()
}

object IrEngine {
    private var toggle = 0

    fun manager(ctx: Context): ConsumerIrManager? =
        ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    fun hasEmitter(ctx: Context): Boolean = manager(ctx)?.hasIrEmitter() == true

    /** Envía una señal por el emisor IR. Devuelve false si no se pudo enviar. */
    fun send(ctx: Context, signal: Signal): Boolean {
        val m = manager(ctx) ?: return false
        if (!m.hasIrEmitter()) return false
        val enc = encode(signal) ?: return false
        return try {
            m.transmit(enc.freq, enc.pattern)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun encode(s: Signal): Enc? = when (s.proto) {
        "NEC" -> nec(s.a, s.c, false)
        "NECext" -> nec(s.a, s.c, true)
        "Samsung32" -> samsung(s.a, s.c)
        "SIRC" -> sirc(s.a, s.c, 5)
        "SIRC15" -> sirc(s.a, s.c, 8)
        "SIRC20" -> sirc(s.a, s.c, 13)
        "RC5", "RC5X" -> rc5(s.a, s.c)
        "RC6" -> rc6(s.a, s.c)
        "RCA" -> rca(s.a, s.c)
        "Kaseikyo" -> kaseikyo(s.a, s.c)
        "RAW" -> if (s.raw.isNotEmpty()) {
            // si termina en marca (cantidad impar) se agrega un silencio final
            val extra = if (s.raw.size % 2 == 1) 1 else 0
            Enc(
                if (s.freq > 0) s.freq else 38000,
                IntArray(s.raw.size + extra) { if (it < s.raw.size) max(s.raw[it], 1) else 30000 }
            )
        } else null
        else -> null
    }

    private fun bit(v: Long, i: Int): Boolean = ((v shr i) and 1L) == 1L

    // NEC: 38 kHz, cabecera 9000/4500, bits LSB primero
    private fun nec(a: Long, c: Long, ext: Boolean): Enc {
        val bytes = if (ext) {
            longArrayOf(a and 255L, (a shr 8) and 255L, c and 255L, (c shr 8) and 255L)
        } else {
            longArrayOf(a and 255L, a.inv() and 255L, c and 255L, c.inv() and 255L)
        }
        val p = Pulses()
        p.mark(9000); p.space(4500)
        for (b in bytes) for (i in 0 until 8) {
            p.mark(560)
            p.space(if (bit(b, i)) 1690 else 560)
        }
        p.mark(560)
        return Enc(38000, p.toArray())
    }

    // Samsung32: 38 kHz, cabecera 4500/4500, direccion repetida + comando + comando invertido
    private fun samsung(a: Long, c: Long): Enc {
        val bytes = longArrayOf(a and 255L, a and 255L, c and 255L, c.inv() and 255L)
        val p = Pulses()
        p.mark(4500); p.space(4500)
        for (b in bytes) for (i in 0 until 8) {
            p.mark(550)
            p.space(if (bit(b, i)) 1650 else 550)
        }
        p.mark(550)
        return Enc(38000, p.toArray())
    }

    // Sony SIRC: 40 kHz, 7 bits de comando + bits de direccion, LSB primero, 3 repeticiones cada 45 ms
    private fun sirc(a: Long, c: Long, addrBits: Int): Enc {
        val p = Pulses()
        for (rep in 0 until 3) {
            val start = p.total()
            p.mark(2400); p.space(600)
            for (i in 0 until 7) {
                p.mark(if (bit(c, i)) 1200 else 600)
                p.space(600)
            }
            for (i in 0 until addrBits) {
                p.mark(if (bit(a, i)) 1200 else 600)
                p.space(600)
            }
            val pad = 45000 - (p.total() - start)
            if (pad > 0) p.space(pad)
        }
        return Enc(40000, p.toArray())
    }

    // RC5 / RC5X: 36 kHz, Manchester, 14 bits (S1, S2/campo, toggle, 5 direccion, 6 comando)
    private fun rc5(a: Long, c: Long): Enc {
        toggle = toggle xor 1
        val bits = ArrayList<Boolean>()
        bits.add(true)                       // S1
        bits.add(c < 64L)                    // S2 = inverso del bit 7 del comando
        bits.add(toggle == 1)
        for (i in 4 downTo 0) bits.add(bit(a, i))
        for (i in 5 downTo 0) bits.add(bit(c, i))
        val p = Pulses()
        for (b in bits) {
            if (b) { p.space(889); p.mark(889) } else { p.mark(889); p.space(889) }
        }
        return Enc(36000, p.toArray())
    }

    // RC6 modo 0: 36 kHz, Manchester, bit de toggle de doble ancho
    private fun rc6(a: Long, c: Long): Enc {
        toggle = toggle xor 1
        val p = Pulses()
        p.mark(2666); p.space(889)
        fun half(b: Boolean, w: Int) {
            if (b) { p.mark(w); p.space(w) } else { p.space(w); p.mark(w) }
        }
        half(true, 444)                      // bit de inicio
        for (i in 0 until 3) half(false, 444) // modo 000
        half(toggle == 1, 889)               // bit de control (doble ancho)
        for (i in 7 downTo 0) half(bit(a, i), 444)
        for (i in 7 downTo 0) half(bit(c, i), 444)
        return Enc(36000, p.toArray())
    }

    // RCA: 56 kHz, 4 bits direccion + 8 comando + ambos invertidos, MSB primero
    private fun rca(a: Long, c: Long): Enc {
        val addr = a and 0xFL
        val cmd = c and 0xFFL
        val p = Pulses()
        p.mark(4000); p.space(4000)
        fun out(v: Long, bits: Int) {
            for (i in bits - 1 downTo 0) {
                p.mark(500)
                p.space(if (bit(v, i)) 2000 else 1000)
            }
        }
        out(addr, 4); out(cmd, 8)
        out(addr.inv() and 0xFL, 4); out(cmd.inv() and 0xFFL, 8)
        p.mark(500); p.space(8000)
        return Enc(56000, p.toArray())
    }

    // Kaseikyo (Panasonic): 37 kHz, 48 bits LSB primero
    private fun kaseikyo(a: Long, c: Long): Enc {
        val vendor = (a shr 8) and 0xFFFFL
        val genre1 = (a shr 4) and 0xFL
        val genre2 = a and 0xFL
        val id = (a shr 24) and 3L
        val cmd = c and 0x3FFL
        val vendorParity = (vendor and 0xFL) xor ((vendor shr 4) and 0xFL) xor
            ((vendor shr 8) and 0xFL) xor ((vendor shr 12) and 0xFL)
        var frame = vendor or (vendorParity shl 16) or (genre1 shl 20) or (genre2 shl 24) or
            (cmd shl 28) or (id shl 38)
        val parity = ((frame shr 16) and 255L) xor ((frame shr 24) and 255L) xor ((frame shr 32) and 255L)
        frame = frame or (parity shl 40)
        val p = Pulses()
        p.mark(3456); p.space(1728)
        for (i in 0 until 48) {
            p.mark(432)
            p.space(if (bit(frame, i)) 1296 else 432)
        }
        p.mark(432)
        return Enc(37000, p.toArray())
    }
}
