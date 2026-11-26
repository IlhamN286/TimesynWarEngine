package com.timesync.autoclicker.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ApplicationProfileRepository(context: Context) {
    private val file = File(context.filesDir, "application_profiles.json")

    @Synchronized
    fun getAll(): List<ApplicationProfile> {
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("profiles") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    runCatching { add(fromJson(array.getJSONObject(i))) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun get(id: String): ApplicationProfile? = getAll().firstOrNull { it.id == id }

    @Synchronized
    fun save(profile: ApplicationProfile): Boolean {
        if (ProfileValidator.validate(profile) != null) return false
        val profiles = getAll().filterNot { it.id == profile.id } + profile
        return writeAll(profiles)
    }

    @Synchronized
    fun delete(id: String): Boolean = writeAll(getAll().filterNot { it.id == id })

    @Synchronized
    fun duplicate(id: String, newName: String): ApplicationProfile? {
        val source = get(id) ?: return null
        val copy = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName,
            layout = source.layout.copy(actions = source.layout.actions.map {
                it.copy(id = java.util.UUID.randomUUID().toString())
            })
        )
        return if (save(copy)) copy else null
    }

    private fun writeAll(profiles: List<ApplicationProfile>): Boolean {
        return try {
            val root = JSONObject().put("schemaVersion", 1).put(
                "profiles",
                JSONArray().apply { profiles.forEach { put(toJson(it)) } }
            )
            val temp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temp).use { it.write(root.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
            if (file.exists() && !file.delete()) return false
            temp.renameTo(file)
        } catch (_: Exception) {
            false
        }
    }

    private fun toJson(profile: ApplicationProfile) = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("packageName", profile.packageName)
        put("timing", JSONObject().apply {
            put("targetTime", profile.timing.targetTime)
            put("executionOffsetMs", profile.timing.executionOffsetMs)
            put("clickIntervalMs", profile.timing.clickIntervalMs)
            put("refreshDelayMs", profile.timing.refreshDelayMs)
        })
        put("layout", JSONObject().apply {
            put("referenceWidth", profile.layout.referenceWidth)
            put("referenceHeight", profile.layout.referenceHeight)
            put("actions", JSONArray().apply {
                profile.layout.actions.forEach { action ->
                    put(JSONObject().apply {
                        put("id", action.id)
                        put("name", action.name)
                        put("x", action.x.toDouble())
                        put("y", action.y.toDouble())
                        put("order", action.order)
                        put("delayAfterMs", action.delayAfterMs)
                        put("enabled", action.enabled)
                    })
                }
            })
        })
    }

    private fun fromJson(json: JSONObject): ApplicationProfile {
        val timing = json.optJSONObject("timing") ?: JSONObject()
        val layout = json.optJSONObject("layout") ?: JSONObject()
        val actions = layout.optJSONArray("actions") ?: JSONArray()
        return ApplicationProfile(
            id = json.optString("id"),
            name = json.optString("name"),
            packageName = json.optString("packageName"),
            timing = TimingConfig(
                targetTime = timing.optString("targetTime", "20:00:00:000"),
                executionOffsetMs = timing.optInt("executionOffsetMs", 0),
                clickIntervalMs = timing.optLong("clickIntervalMs", 250L),
                refreshDelayMs = timing.optLong("refreshDelayMs", 320L)
            ),
            layout = LayoutConfig(
                referenceWidth = layout.optInt("referenceWidth", 1080),
                referenceHeight = layout.optInt("referenceHeight", 2400),
                actions = buildList {
                    for (i in 0 until actions.length()) {
                        val a = actions.getJSONObject(i)
                        add(ActionPoint(
                            id = a.optString("id"),
                            name = a.optString("name", "Action"),
                            x = a.optDouble("x").toFloat(),
                            y = a.optDouble("y").toFloat(),
                            order = a.optInt("order", i + 1),
                            delayAfterMs = a.optLong("delayAfterMs", 0L),
                            enabled = a.optBoolean("enabled", true)
                        ))
                    }
                }
            )
        )
    }
}
