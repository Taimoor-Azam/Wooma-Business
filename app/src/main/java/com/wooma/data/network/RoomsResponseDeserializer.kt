package com.wooma.data.network

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.wooma.model.RoomsResponse
import java.lang.reflect.Type

/**
 * Tenant-report room objects may carry inventory under `room_items`, `inventory_items`, etc.
 * Default Gson only bound `items`, so hydration skipped and the room-items screen had nothing offline.
 */
class RoomsResponseDeserializer(
    private val delegate: Gson
) : JsonDeserializer<RoomsResponse> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): RoomsResponse {
        if (!json.isJsonObject) {
            return delegate.fromJson(json, RoomsResponse::class.java)
        }
        val o = json.asJsonObject.deepCopy()
        if (shouldFillItemsFromAlternate(o)) {
            for (key in ITEM_ARRAY_KEYS) {
                val alt = o.get(key) ?: continue
                if (alt.isJsonArray && alt.asJsonArray.size() > 0) {
                    o.add("items", alt.deepCopy())
                    break
                }
            }
        }
        return delegate.fromJson(o, RoomsResponse::class.java)
    }

    private fun shouldFillItemsFromAlternate(o: JsonObject): Boolean {
        val items = o.get("items")
        if (items == null || !items.isJsonArray) return true
        return items.asJsonArray.size() == 0
    }

    private companion object {
        val ITEM_ARRAY_KEYS = arrayOf(
            "room_items",
            "inventory_items",
            "room_line_items",
            "line_items"
        )
    }
}
