package com.couchjoust

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONObject

/** ATT.ASM STRIP / FLASH: three strips per tick, eight rotating palette entries. */
class Marquee(context:Context) {
    private val source=context.assets.open("graphics/title-line-data.json").bufferedReader().use { JSONObject(it.readText()) }
    private val registers=source.getJSONArray("palette_register_blocks")
    private val indices=IntArray(304*256)
    private val appears=IntArray(indices.size)
    private val pixels=IntArray(indices.size)
    private val bitmap=Bitmap.createBitmap(304,256,Bitmap.Config.ARGB_8888)
    private val colors=IntArray(16)
    private var previous=-1
    private var count=-1
    private var paletteBlock=8
    private var dump=0
    private var cursor=0
    private var strips=0
    init {
        var color=8
        fun strip(x:Int,y:Int,vertical:Boolean) {
            color=(color+1 and 15) or 8
            for(i in 0..15) {
                val px=x+if(vertical) 0 else i
                val py=y+if(vertical) i else 0
                if(px in 0..303 && py in 0..255) {
                    val at=py*304+px
                    if(indices[at]==0) { indices[at]=(color+i and 15) or 8;appears[at]=(strips+2)/3 }
                }
            }
            strips++
        }
        for(x in 16..302) strip(x,16,true)
        for(y in 32..242) strip(286,y,false)
        for(x in 285 downTo 0) strip(x,226,true)
        for(y in 225 downTo 16) strip(0,y,false)
    }
    fun draw(canvas:Canvas,paint:Paint,tick:Int) {
        if(tick<previous || previous<0) {
            for(i in colors.indices) colors[i]=registers.getInt(i)
            count=-1;paletteBlock=8;dump=0;cursor=0;previous=-1
        }
        for(t in previous+1..tick) if(t>0 && t%2==0) {
            var next=colors[15]
            if(count<0 && t>=(strips+2)/3) count=16
            if(count>=0) {
                if(count>0) {
                    count--
                    if(count==0) {
                        paletteBlock+=8
                        if(paletteBlock>=registers.length()) paletteBlock=8
                        cursor=paletteBlock;dump=8
                    }
                }
                if(count==0) {
                    next=registers.getInt(cursor++)
                    if(--dump==0) count=87
                }
            }
            for(i in 15 downTo 9) colors[i]=colors[i-1]
            colors[8]=next
        }
        previous=tick
        for(i in pixels.indices) {
            val register=colors[indices[i]]
            pixels[i]=if(indices[i]==0 || appears[i]>tick) Color.TRANSPARENT else
                Color.rgb((register and 7)*255/7,((register shr 3) and 7)*255/7,((register shr 6) and 3)*255/3)
        }
        bitmap.setPixels(pixels,0,304,0,0,304,256)
        canvas.drawBitmap(bitmap,0f,0f,paint)
    }
}
