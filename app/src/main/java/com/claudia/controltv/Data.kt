package com.claudia.controltv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Una señal IR: protocolo + direccion + comando, o una señal cruda (RAW) con sus duraciones. */
class Signal(
    val proto: String,
    val a: Long = 0L,
    val c: Long = 0L,
    val freq: Int = 0,
    val raw: IntArray = IntArray(0)
) {
    fun toJson(): JSONArray {
        val arr = JSONArray()
        arr.put(proto)
        if (proto == "RAW") {
            arr.put(freq)
            val d = JSONArray()
            for (v in raw) d.put(v)
            arr.put(d)
        } else {
            arr.put(a)
            arr.put(c)
        }
        return arr
    }

    /** Clave para saber si dos señales son iguales. */
    val key: String get() = toJson().toString()

    companion object {
        fun fromJson(arr: JSONArray): Signal {
            val proto = arr.getString(0)
            if (proto == "RAW") {
                val d = arr.getJSONArray(2)
                return Signal(proto, freq = arr.getInt(1), raw = IntArray(d.length()) { d.getInt(it) })
            }
            return Signal(proto, a = arr.getLong(1), c = arr.getLong(2))
        }
    }
}

/** Un perfil = un "numero de codigo" de una marca, con todos sus botones. */
class Profile(val n: Int, val model: String, val buttons: Map<String, Signal>) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("n", n)
        o.put("m", model)
        val b = JSONObject()
        for ((k, v) in buttons) b.put(k, v.toJson())
        o.put("b", b)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val b = o.getJSONObject("b")
            val map = LinkedHashMap<String, Signal>()
            val keys = b.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = Signal.fromJson(b.getJSONArray(k))
            }
            return Profile(o.getInt("n"), o.optString("m", ""), map)
        }
    }
}

class Brand(val name: String, val slug: String, val count: Int)
class BrandData(val name: String, val profiles: List<Profile>)

/** TV guardado por el usuario: lleva su perfil completo, asi funciona sin internet. */
class SavedTv(val id: Long, val name: String, val brand: String, val profile: Profile) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("brand", brand)
        o.put("profile", profile.toJson())
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): SavedTv =
            SavedTv(o.getLong("id"), o.getString("name"), o.getString("brand"), Profile.fromJson(o.getJSONObject("profile")))
    }
}

class TvStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    fun load(): List<SavedTv> {
        return try {
            val arr = JSONArray(sp.getString("list", "[]"))
            List(arr.length()) { SavedTv.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<SavedTv>) {
        val arr = JSONArray()
        for (t in list) arr.put(t.toJson())
        sp.edit().putString("list", arr.toString()).apply()
    }
}

/** Descarga solo lo que se pide (indice de marcas y la marca elegida) y lo guarda en cache. */
object Library {
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    private suspend fun fetch(ctx: Context, fileName: String): String = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "brands")
        dir.mkdirs()
        val cache = File(dir, fileName)
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < MAX_AGE_MS
        if (fresh) {
            cache.readText()
        } else {
            try {
                val con = URL(BuildConfig.BRANDS_URL + fileName).openConnection() as HttpURLConnection
                con.connectTimeout = 10000
                con.readTimeout = 20000
                if (con.responseCode != 200) throw IOException("HTTP " + con.responseCode)
                val text = con.inputStream.bufferedReader().use { it.readText() }
                cache.writeText(text)
                text
            } catch (e: Exception) {
                if (cache.exists()) cache.readText() else throw e
            }
        }
    }

    suspend fun index(ctx: Context): List<Brand> {
        val arr = JSONArray(fetch(ctx, "index.json"))
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            Brand(o.getString("n"), o.getString("s"), o.getInt("c"))
        }
    }

    suspend fun brand(ctx: Context, b: Brand): BrandData {
        val o = JSONObject(fetch(ctx, b.slug + ".json"))
        val arr = o.getJSONArray("profiles")
        val list = List(arr.length()) { Profile.fromJson(arr.getJSONObject(it)) }
        return BrandData(o.optString("brand", b.name), list)
    }

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** Busca por nombre: si hay coincidencia exacta devuelve solo esa; si no, las que contengan el texto. */
    fun search(index: List<Brand>, query: String): List<Brand> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val exact = index.filter { norm(it.name) == q }
        if (exact.isNotEmpty()) return exact
        return index.filter { norm(it.name).contains(q) }
    }
}
