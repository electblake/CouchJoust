package com.couchjoust

import android.content.Context
import android.graphics.*
import com.couchjoust.core.*
import org.json.JSONObject

class Artwork(context:Context) {
    private val manifest=context.assets.open("graphics/asset-manifest.json").bufferedReader().use { JSONObject(it.readText()) }
    val palette=IntArray(16) { i -> val c=manifest.getJSONArray("palette_rgb").getJSONArray(i);Color.rgb(c.getInt(0),c.getInt(1),c.getInt(2)) }
    val sprites=mutableMapOf<String,Bitmap>()
    private val shiftedSprites=mutableMapOf<String,Bitmap>()
    private val fonts=mutableMapOf<String,Bitmap>()
    private val reserveIcons=arrayOf(
        arrayOf("005100","005500","005000","055500","055500","005550","011055"),
        arrayOf("005700","007700","000700","007770","007770","077700","771010")
    ).map { rows -> Bitmap.createBitmap(IntArray(42) { i -> val index=rows[i/6][i%6].digitToInt(16);if(index==0) Color.TRANSPARENT else palette[index] },6,7,Bitmap.Config.ARGB_8888) }
    private val characterCodes="0123456789 ABCDEFGHIJKLMNOPQRSTUVWXYZ<=-?!()',./&\":".withIndex().associate { it.value to it.index }
    val terrain=manifest.getJSONObject("terrain")
    private val titleData=context.assets.open("graphics/title-line-data.json").bufferedReader().use { JSONObject(it.readText()) }
    val title=Bitmap.createBitmap(IntArray(304*256) { i ->
        val index=titleData.getJSONArray("index_rows").getString(i/304)[i%304].digitToInt(16)
        if(index==0) Color.TRANSPARENT else {
            val rgb=titleData.getJSONArray("initial_palette_rgb").getJSONArray(index)
            Color.rgb(rgb.getInt(0),rgb.getInt(1),rgb.getInt(2))
        }
    },304,256,Bitmap.Config.ARGB_8888)
    val paint=Paint().apply { isFilterBitmap=false;isAntiAlias=false }
    val surface=Bitmap.createBitmap(304,256,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(surface)
    init {
        for(section in listOf("sprites","terrain")) {
            val records=manifest.getJSONObject(section)
            for(key in records.keys()) sprites[key]=decode(records.getJSONObject(key))
        }
        sprites["BOTTOM"]=decode(manifest.getJSONObject("compressed_cliff"))
        val effects=context.assets.open("graphics/special-effects.json").bufferedReader().use { JSONObject(it.readText()) }
        for(group in listOf("cliff_debris","pterodactyl_ashes")) {
            val frames=effects.getJSONObject(group).getJSONArray("frames")
            for(i in 0 until frames.length()) {
                val record=frames.getJSONObject(i)
                sprites[record.getString("name")]=decode(record)
            }
        }
        val glyphs=manifest.getJSONObject("font_glyphs")
        for(key in glyphs.keys()) fonts[key]=decode(glyphs.getJSONObject(key))
        for((name,bitmap) in sprites) {
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val shifted=IntArray(pixels.size) { i -> if(i==0) 0 else pixels[i-1] }
            shiftedSprites[name]=Bitmap.createBitmap(shifted,bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
        }
    }
    private fun decode(record:JSONObject):Bitmap {
        val rows=record.getJSONArray("index_rows")
        val width=record.getInt("width_pixels")
        val height=record.getInt("height_pixels")
        val pixels=IntArray(width*height) { i -> val index=rows.getString(i/width)[i%width].digitToInt(16);if(index==0) Color.TRANSPARENT else palette[index] }
        return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
    }
    fun sprite(name:String,x:Int,y:Int) {
        val odd=x and 1
        canvas.drawBitmap(if(odd==0) sprites.getValue(name) else shiftedSprites.getValue(name),(x-odd).toFloat(),y.toFloat(),paint)
    }
    fun frame(descriptor:Frame,x:Int,y:Int) { sprite(descriptor.sprite,x+descriptor.dx,y+descriptor.dy) }
    fun page(name:String,large:Boolean=true) {
        val table=manifest.getJSONObject("font_tables").getJSONArray(if(large) "FONT57" else "FONT35")
        for(line in Tables.textPages.getValue(name)) {
            var x=line.x
            paint.colorFilter=PorterDuffColorFilter(palette[line.color],PorterDuff.Mode.SRC_IN)
            for(code in Tables.phrases.getValue(line.phrase)) {
                val glyph=fonts.getValue(table.getString(code))
                canvas.drawBitmap(glyph,x.toFloat(),line.y.toFloat(),paint);x+=glyph.width
            }
        }
        paint.colorFilter=null
    }
    fun text(text:String,x:Int,y:Int,color:Int=1,large:Boolean=false) {
        var cursor=x
        paint.colorFilter=PorterDuffColorFilter(palette[color],PorterDuff.Mode.SRC_IN)
        for(char in text.uppercase()) {
            val code=characterCodes.getValue(char)
            val name=manifest.getJSONObject("font_tables").getJSONArray(if(large) "FONT57" else "FONT35").getString(code)
            val glyph=fonts.getValue(name)
            canvas.drawBitmap(glyph,cursor.toFloat(),y.toFloat(),paint)
            cursor+=glyph.width
        }
        paint.colorFilter=null
    }
    fun centered(text:String,y:Int,color:Int=1,large:Boolean=false) {
        val table=manifest.getJSONObject("font_tables").getJSONArray(if(large) "FONT57" else "FONT35")
        val width=text.uppercase().sumOf { fonts.getValue(table.getString(characterCodes.getValue(it))).width }
        text(text,152-width/2,y,color,large)
    }
    fun arena(game:Game) {
        canvas.drawColor(Color.BLACK)
        val t=game.terrain
        paint.color=palette[4]
        canvas.drawRect(0f,t.lavaY.toFloat(),304f,247f,paint)
        if(t.bubbleStage>=0) {
            fun lavaPixel(dx:Int,dy:Int,width:Int) {
                paint.color=Color.BLACK
                canvas.drawRect((t.bubbleX+dx).toFloat(),(t.bubbleY+dy).toFloat(),(t.bubbleX+dx+width).toFloat(),(t.bubbleY+dy+1).toFloat(),paint)
            }
            if(t.surfaceBubble) {
                lavaPixel(0,1-t.bubbleStage,2)
                if(t.bubbleStage==2) {
                    paint.color=palette[4]
                    for(dx in intArrayOf(-2,2)) canvas.drawRect((t.bubbleX+dx).toFloat(),(t.bubbleY-1).toFloat(),(t.bubbleX+dx+2).toFloat(),t.bubbleY.toFloat(),paint)
                }
            } else if(t.bubbleStage==0) lavaPixel(1,0,1)
            else { lavaPixel(0,0,1);lavaPixel(2,0,1);lavaPixel(1,-1,1);lavaPixel(1,1,1) }
        }
        sprite("BOTTOM",54,211)
        for(key in terrain.keys()) {
            if(game.demo && key!="_CLIF5" && key!="_TRANS4") continue
            val name=key.removePrefix("_")
            val record=terrain.getJSONObject(key)
            if(name in t.disabled) {
                val age=t.destruction.getValue(name)
                if(age<100) {
                    sprite(key,record.getInt("x_pixels"),record.getInt("y_pixels"))
                    if(age/10%2==1) sprite(key,record.getInt("x_pixels")+1,record.getInt("y_pixels"))
                } else if(age in 102..141) {
                    val x=when(name) { "CLIF1L" -> -30;"CLIF2" ->98;else ->record.getInt("x_pixels") }
                    sprite("DEBRIS_${(age-102)/8+1}",x,record.getInt("y_pixels"))
                }
                continue
            }
            if(key.startsWith("_TRANS") && !t.support(record.getInt("x_pixels")+7,record.getInt("y_pixels")-1)) continue
            sprite(key,record.getInt("x_pixels"),record.getInt("y_pixels"))
        }
        paint.color=palette[10]
        if(t.burnLeft<45) canvas.drawRect(maxOf(0,t.burnLeft).toFloat(),211f,54f,213f,paint)
        if(t.burnRight>230) canvas.drawRect(240f,211f,minOf(304,t.burnRight).toFloat(),213f,paint)
        t.flames.filter { it.visible }.forEach { frame(Tables.frames.getValue("_IFLAME")[it.frame and 3],it.x,it.y) }
        for(actor in game.scheduler.actors()) drawActor(actor,game.tick)
        for(p in game.players) {
            if(p.index==1 && p.lives==0 && p.score==0L) continue
            val x=if(p.index==0) 72 else 150
            val color=if(p.index==0) 5 else 7
            text((p.score%10_000_000).toString().padStart(7,if(p.score>=10_000_000) '0' else ' '),x,217,color,true)
            repeat(minOf(5,p.reserves)) { i ->
                canvas.drawBitmap(reserveIcons[p.index],(x+42+i*6).toFloat(),217f,paint)
            }
        }
        if(game.demo) {
            game.demonstration.pages.forEach { page(it,it!="ATX13") }
            if(game.demonstration.lavaWarning) {
                text(Tables.messages.getValue("MSW19"),232,220,4)
                text(Tables.messages.getValue("MSW20"),236,227,4)
            }
        } else if(game.director.state==0) {
            text("WAVE ${game.waveNumber.toString().padStart(2,' ')}",116,96,1,true)
            when(game.special) {
                "intro" -> {
                    text("PREPARE TO JOUST",90,125,1,true)
                    if(game.director.timer<=90) text("BUZZARD BAIT!",102,155,1,true)
                }
                "egg" -> text("EGG WAVE",112,125,1,true)
                "team_or_survival" -> if(game.teamWave) {
                    text("TEAM WAVE",108,125,1,true)
                    text(Tables.messages.getValue("MSW03"),56,155,1,true)
                } else text("SURVIVAL WAVE",98,125,1,true)
                "gladiator" -> if(game.teamWave) {
                    text("GLADIATOR WAVE",96,127,1,true)
                    if(game.director.timer<=90) {
                        text("3000 POINT BOUNTY",92,144,1,true)
                        text("FOR DISMOUNTING FIRST PLAYER",56,155,1,true)
                    }
                }
                "pterodactyl" -> text(Tables.messages.getValue("MSW07"),34,120,1,true)
            }
        }
        game.waveResults.forEach { text(Tables.messages.getValue(it.symbol),it.x,it.y,it.color,true) }
        if(game.gameOver) text("THY GAME IS OVER",96,144,1,true)
        else game.players.filter { it.overMessage>0 }.forEach { text("PLAYER ${it.index+1} GAME OVER",82,108,if(it.index==0) 5 else 7,true) }
    }
    private fun drawActor(a:Actor,tick:Long) {
        when(a) {
            is Bird -> {
                if(a.mode==BirdMode.RESPAWN || a.mode==BirdMode.GONE || a.mode==BirdMode.QUEUED) return
                canvas.save()
                val materializing=a.mode==BirdMode.MATERIALIZE
                val protected=a.mode==BirdMode.PROTECTED
                val own=if(a.owner==0) 5 else if(a.owner==1) 7 else 1
                if(materializing || protected) {
                    paint.colorFilter=PorterDuffColorFilter(palette[if(materializing) own else a.protectionColors[2]],PorterDuff.Mode.SRC_IN)
                    sprite("_TRANS${a.transporter+1}",a.x-7,a.y+1);paint.colorFilter=null
                }
                if(materializing && a.stateTimer>20) { canvas.restore();return }
                if(a.mode==BirdMode.SINK) canvas.clipRect(0,0,304,224)
                fun component(descriptor:Frame,index:Int) {
                    val color=if(materializing) own else a.protectionColors[index]
                    if(materializing || protected && !(a.protectionPhase==1 && color==own && a.owner>=0))
                        paint.colorFilter=PorterDuffColorFilter(palette[color],PorterDuff.Mode.SRC_IN)
                    if(materializing) {
                        val h=((a.stateTimer.inv() shr 1) and 15)
                        val shift=(2-h) and 15
                        val bitmap=sprites.getValue(descriptor.sprite)
                        val y=a.y+descriptor.dy+shift
                        val rows=minOf(h,bitmap.height,a.y+1-y)
                        if(rows>0) canvas.drawBitmap(bitmap,Rect(0,0,bitmap.width,rows),Rect(a.x+descriptor.dx,y,a.x+descriptor.dx+bitmap.width,y+rows),paint)
                    } else frame(descriptor,a.x,a.y)
                    paint.colorFilter=null
                }
                if(a.ridden) {
                    val name=if(a.owner>=0) "_PLYR${a.owner+1}" else "_PLYR${a.species+3}"
                    val index=(if(a.lance==2) 0 else 2)+(if(a.facing<0) 1 else 0)
                    component(Tables.frames.getValue(name)[index],0)
                }
                component(a.descriptor,1)
                paint.colorFilter=null
                canvas.restore()
            }
            is Egg -> {
                if(a.state==4) return
                if(a.state==3) sprite("PLY${a.species+3}S",a.x,a.y-11)
                else frame(a.descriptor,a.x,a.y)
            }
            is Popup -> text(a.points.toString(),a.x,a.y-6,1)
            is Troll -> {
                if(a.stage==0) return
                canvas.save();canvas.clipRect(0,0,304,224);frame(a.descriptor,a.x,a.y);canvas.restore()
            }
            is TrollFront -> {
                canvas.save();canvas.clipRect(0,0,304,224);frame(a.descriptor,a.x,a.y);canvas.restore()
            }
            is Pterodactyl -> {
                if(a.hidden) return
                if(a.deathAge<60) frame(a.descriptor,a.x,a.y)
                else if(a.deathAge<84 && (a.deathAge-60)%8<2) sprite("ASH_${(a.deathAge-60)/8+1}",a.x,a.y-10)
            }
            else -> frame(a.descriptor,a.x,a.y)
        }
    }
    fun blit(target:Canvas,width:Int,height:Int,overscan:Float) {
        target.drawColor(Color.BLACK)
        val h=minOf(height.toFloat(),width*0.75f)*overscan
        val w=h*4f/3f
        target.drawBitmap(surface,Rect(6,7,298,247),RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint)
    }
}
