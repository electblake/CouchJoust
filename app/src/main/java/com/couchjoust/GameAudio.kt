package com.couchjoust

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.couchjoust.core.*
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GameAudio(context:Context) {
    private val track=AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(maxOf(1920,AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)))
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    private data class Clip(val pcm:ShortArray,val firstWrite:Int)
    private val clips=mutableMapOf<Int,Clip>()
    private val lock=Any()
    private var clip:Clip?=null
    private var cursor=0
    private var held:Short=0
    private var suspended=false
    @Volatile private var running=true
    private val worker:Thread
    val ready=true
    var enabled=true
        set(value) { field=value;track.setVolume(if(value) 1f else 0f) }
    val sequencer:SoundSequencer
    init {
        val manifest=context.assets.open("audio/audio-manifest.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("commands") }
        for(i in 1..39) {
            val key=i.toString(16).padStart(2,'0')
            val bytes=context.assets.open("audio/commands/command_$key.wav").use { it.readBytes() }
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var offset=12
            while(String(bytes,offset,4,Charsets.US_ASCII)!="data") offset+=8+buffer.getInt(offset+4)
            val pcm=ShortArray(buffer.getInt(offset+4)/2) { n -> buffer.getShort(offset+8+n*2) }
            clips[i]=Clip(pcm,(manifest.getJSONObject(key).getDouble("first_dac_write_seconds")*48000).toInt())
        }
        val source=context.assets.open("sound-events.json").bufferedReader().use { JSONObject(it.readText()) }
        val events=source.keys().asSequence().associateWith { name ->
            val e=source.getJSONObject(name)
            val seq=e.getJSONArray("sequence")
            SoundEvent(e.getInt("priority"),List(seq.length()) { i ->
                val s=seq.getJSONObject(i);SoundCommand(s.getInt("command"),s.getInt("hold_ticks"))
            })
        }
        sequencer=SoundSequencer(events) { command ->
            synchronized(lock) { clip=if(command==63) null else clips.getValue(command);cursor=0 }
        }
        track.play()
        worker=Thread({
            val output=ShortArray(480)
            while(running) {
                synchronized(lock) {
                    for(i in output.indices) {
                        if(suspended) output[i]=0
                        else {
                            val current=clip
                            if(current!=null && cursor<current.pcm.size) {
                                if(cursor>=current.firstWrite) held=current.pcm[cursor]
                                cursor++
                            }
                            output[i]=held
                        }
                    }
                }
                track.write(output,0,output.size,AudioTrack.WRITE_BLOCKING)
            }
        },"Joust PCM")
        worker.start()
    }
    fun pause() { synchronized(lock) { suspended=true } }
    fun resume() { synchronized(lock) { suspended=false } }
    fun close() { running=false;worker.join();track.stop();track.release() }
}
