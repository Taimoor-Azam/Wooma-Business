package com.wooma.business.storage

import android.content.Context
import com.google.gson.Gson
import com.wooma.business.model.User

object Prefs {

    private const val PREF_NAME = "app_prefs"
    private const val KEY_USER = "key_user"

    fun saveUser(context: Context, response: User) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val json = Gson().toJson(response)
        editor.putString(KEY_USER, json)
        editor.apply()
    }

    fun getUser(context: Context): User? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_USER, null)

        return if (json != null) {
            Gson().fromJson(json, User::class.java)
        } else {
            null
        }
    }

    fun clearUser(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_USER).apply()
    }
}