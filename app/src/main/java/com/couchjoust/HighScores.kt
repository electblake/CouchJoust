package com.couchjoust

import android.content.SharedPreferences
import com.couchjoust.core.Tables
import org.json.JSONArray
import org.json.JSONObject

class HighScores(private val preferences:SharedPreferences) {
    companion object {
        private val sessionScores=Tables.defaultScores.subList(26,32).map { ScoreEntry(it.first,it.second) }.toMutableList()
    }
    val today=sessionScores
    val allTime=mutableListOf<ScoreEntry>()
    var champion=ScoreEntry(Tables.defaultScores[0].first,"WIL")
    init {
        if(preferences.contains("allTime")) {
            val stored=JSONArray(preferences.getString("allTime",null)!!)
            for(i in 0 until stored.length()) {
                val s=stored.getJSONObject(i);allTime.add(ScoreEntry(s.getLong("score"),s.getString("name")))
            }
            champion=ScoreEntry(preferences.getLong("championScore",Tables.defaultScores[0].first),preferences.getString("championName","WIL")!!)
        } else allTime.addAll(Tables.defaultScores.subList(1,41).map { ScoreEntry(it.first,it.second) })
    }
    fun qualifies(score:Long)=score>today.last().score || score>allTime.last().score || score>champion.score
    fun submit(entry:ScoreEntry) {
        fun insert(table:MutableList<ScoreEntry>) {
            val position=table.indexOfFirst { entry.score>it.score }
            if(position>=0) { table.add(position,entry);table.removeAt(table.lastIndex) }
        }
        insert(today);insert(allTime)
        if(entry.score>champion.score) champion=entry
        val stored=JSONArray()
        allTime.forEach { stored.put(JSONObject().put("score",it.score).put("name",it.name)) }
        preferences.edit().putString("allTime",stored.toString()).putLong("championScore",champion.score).putString("championName",champion.name).apply()
    }
}
