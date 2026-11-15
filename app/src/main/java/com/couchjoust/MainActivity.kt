package com.couchjoust

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.couchjoust.core.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity:ComponentActivity(),InputManager.InputDeviceListener {
    lateinit var gameView:GameView
    private lateinit var inputs:InputManager
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        gameView=GameView(this)
        setContentView(gameView)
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                gameView.key(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK))
                gameView.key(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK))
            }
        })
        inputs=getSystemService(InputManager::class.java)
        inputs.registerInputDeviceListener(this,null)
    }
    override fun onGenericMotionEvent(event:MotionEvent):Boolean = gameView.motion(event) || super.onGenericMotionEvent(event)
    override fun onPause() { gameView.suspendGame();super.onPause() }
    override fun onResume() { super.onResume();gameView.resumeHost() }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus);if(hasFocus) gameView.resumeHost() else gameView.suspendGame() }
    override fun onDestroy() { inputs.unregisterInputDeviceListener(this);gameView.close();super.onDestroy() }
    override fun onInputDeviceAdded(deviceId:Int) { gameView.reconnect(deviceId) }
    override fun onInputDeviceChanged(deviceId:Int) {}
    override fun onInputDeviceRemoved(deviceId:Int) { gameView.disconnect(deviceId) }
}

data class ScoreEntry(val score:Long,val name:String)

class GameView(context:Context):View(context),Choreographer.FrameCallback {
    override fun dispatchKeyEvent(event:KeyEvent):Boolean =
        (event.keyCode!=KeyEvent.KEYCODE_BACK && key(event)) || super.dispatchKeyEvent(event)
    val artwork=Artwork(context)
    private val marquee=Marquee(context)
    val audio=GameAudio(context)
    val game=Game { name,active -> audio.sequencer.request(name,active) }
    private val preferences=context.getSharedPreferences("joust",Context.MODE_PRIVATE)
    var screen="TITLE"
    var menu=0
    var previousNanos=0L
    var accumulator=0L
    var hostActive=true
    var titleTicks=0
    var credits=0
    var freePlay=preferences.getBoolean("freePlay",true)
    var overscan=preferences.getFloat("overscan",1f)
    var soundOn=preferences.getBoolean("sound",true)
    val assignments=mutableMapOf<Int,Int>()
    val descriptors=arrayOfNulls<String>(2)
    private var joining=false
    private var firstJoin:Int?=null
    private var initialPlayer=0
    private var initialCharacter=1
    private var initialName=""
    private var initialsQueue=mutableListOf<Int>()
    private var waitingForScorePage=false
    val highScores=HighScores(preferences)
    var scorePage=0
    init {
        isFocusable=true;isFocusableInTouchMode=true;requestFocus()
        audio.enabled=soundOn
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun doFrame(frameTimeNanos:Long) {
        if(hostActive) {
            if(previousNanos!=0L) accumulator+=frameTimeNanos-previousNanos
            previousNanos=frameTimeNanos
            while(accumulator>=GameMath.TICK_NS) {
                accumulator-=GameMath.TICK_NS
                if(!game.paused) audio.sequencer.tick()
                if(screen=="PLAY" || screen=="DEMO") {
                    game.step()
                    if(screen=="PLAY" && game.gameOver && game.overTimer==88) qualify()
                    if(screen=="PLAY" && waitingForScorePage && game.overTimer>=960) { waitingForScorePage=false;nextInitials() }
                    if(screen=="DEMO" && game.demonstration.complete) { screen="SCORES";titleTicks=0;game.active=false }
                } else if(screen=="TITLE" || screen=="SCORES") {
                    game.advanceRandomClock()
                    titleTicks++
                    if(screen=="TITLE" && titleTicks>=1110 && audio.ready) { game.start(1,true);screen="DEMO" }
                    else if(screen=="SCORES" && titleTicks>=900) { screen="TITLE";titleTicks=0 }
                }
            }
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onDraw(canvas:Canvas) {
        val art=artwork
        if(screen in listOf("PLAY","PAUSE","DEMO")) {
            art.arena(game)
            if(screen=="PAUSE") {
                art.paint.color=Color.BLACK;art.canvas.drawRect(52f,84f,252f,174f,art.paint)
                art.centered("PAUSED",94,5,true)
                listOf("RESUME","RESTART","CONTROLS","QUIT TO TITLE").forEachIndexed { i,s -> art.centered((if(menu==i) "- " else "  ")+s,113+i*13,if(menu==i) 5 else 1) }
            }
        } else {
            art.canvas.drawColor(Color.BLACK)
            when(screen) {
                "TITLE" -> {
                    marquee.draw(art.canvas,art.paint,titleTicks)
                    art.canvas.drawBitmap(art.title,0f,0f,art.paint)
                    listOf("ONE PLAYER","TWO PLAYERS","HIGH SCORES","CONTROLS","SETTINGS","TWO TV CONNECTION TEST").forEachIndexed { i,s -> art.centered((if(menu==i) "- " else "  ")+s,124+i*12,if(menu==i) 5 else 1) }
                    art.centered(if(audio.ready) "CENTER TO SELECT" else "LOADING ORIGINAL SOUNDS",196,7)
                    art.centered(if(freePlay) "FREE PLAY" else "CREDITS $credits",208,1)
                    art.centered("1982 WILLIAMS ELECTRONICS",220,1)
                }
                "JOIN" -> {
                    art.centered("TWO PLAYERS",55,5,true)
                    art.centered(if(firstJoin==null) "PLAYER ONE PRESS CENTER" else "PLAYER TWO PRESS CENTER",100,1)
                    art.centered("USE A DIFFERENT REMOTE OR CONTROLLER",120,7)
                    art.centered("BACK TO CANCEL",190,1)
                }
                "CONTROLS" -> {
                    art.centered("HOW TO JOUST",45,5,true)
                    listOf("LEFT AND RIGHT TO MOVE","CENTER OR ENTER TO FLAP","RELEASE AND PRESS FOR EACH FLAP","HOLDING DOES NOT REPEAT","HIGHER LANCE WINS THE JOUST","COLLECT EGGS BEFORE THEY HATCH","BACK TO PAUSE","TWO PLAYERS NEED TWO DEVICES","CENTER OR BACK TO RETURN").forEachIndexed { i,s -> art.centered(s,76+i*16,if(i==1) 7 else 1) }
                }
                "SETTINGS" -> {
                    art.centered("SETTINGS",45,5,true)
                    listOf("FREE PLAY ${if(freePlay) "ON" else "OFF"}","SCREEN SIZE ${(overscan*100).toInt()}","SOUND ${if(soundOn) "ON" else "OFF"}","RETURN").forEachIndexed { i,s -> art.centered((if(menu==i) "- " else "  ")+s,95+i*22,if(menu==i) 5 else 1) }
                    art.centered("LEFT OR RIGHT TO CHANGE",205,7)
                }
                "SCORES" -> {
                    art.centered(if(scorePage==0) "TODAYS GREATEST" else "ALL TIME GREATEST",35,5,true)
                    val champion=highScores.champion
                    art.centered("CHAMPION ${champion.name} ${champion.score}",55,7)
                    val records=if(scorePage==0) highScores.today else highScores.allTime.drop((scorePage-1)*6).take(6)
                    records.forEachIndexed { i,s -> art.centered("${i+1}  ${s.name}  ${s.score.toString().padStart(7,'0')}",85+i*18,if(i==0) 7 else 1,true) }
                    art.centered("LEFT RIGHT FOR MORE",204,1)
                    art.centered("CENTER TO RETURN",220,5)
                }
                "INITIALS" -> {
                    art.centered("HIGH SCORE",45,5,true)
                    art.centered("PLAYER ${initialPlayer+1}",75,7)
                    art.centered(initialName+character()+"-".repeat(2-initialName.length),115,1,true)
                    art.centered("LEFT RIGHT TO CHOOSE",160,1)
                    art.centered("CENTER TO ACCEPT",180,5)
                }
            }
        }
        art.blit(canvas,width,height,overscan)
    }
    private fun character()=when(initialCharacter) { 0->" ";27->"<";else->('A'.code+initialCharacter-1).toChar().toString() }
    fun key(e:KeyEvent):Boolean {
        val code=e.keyCode
        val flap=code in intArrayOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER,KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_SPACE)
        val left=code==KeyEvent.KEYCODE_DPAD_LEFT
        val right=code==KeyEvent.KEYCODE_DPAD_RIGHT
        val back=code==KeyEvent.KEYCODE_BACK || code==KeyEvent.KEYCODE_BUTTON_B
        val start=code==KeyEvent.KEYCODE_BUTTON_START || code==KeyEvent.KEYCODE_MENU
        val up=code==KeyEvent.KEYCODE_DPAD_UP
        val down=code==KeyEvent.KEYCODE_DPAD_DOWN
        val select=code==KeyEvent.KEYCODE_BUTTON_SELECT
        val two=code==KeyEvent.KEYCODE_BUTTON_Y
        if(!flap && !left && !right && !back && !start && !up && !down && !select && !two) return false
        val pressed=e.action==KeyEvent.ACTION_DOWN
        if(screen=="PLAY") {
            if(pressed && e.repeatCount==0 && (back || start)) { pause();return true }
            val player=assignments[e.deviceId]
            if(player!=null) {
                val c=game.controls[player]
                if(left) c.leftKey=pressed
                if(right) c.rightKey=pressed
                if(flap) c.flapKey=pressed
            }
            return true
        }
        if(!pressed || e.repeatCount!=0) return true
        if(select) { credits++;audio.sequencer.request("SNCRED",false);return true }
        if(screen=="DEMO") { screen="TITLE";titleTicks=0;game.active=false;menu=0;return true }
        when(screen) {
            "TITLE" -> {
                if(up) menu=(menu+5)%6
                if(down) menu=(menu+1)%6
                if(two) { screen="JOIN";firstJoin=null;joining=true }
                if(flap || start) {
                    if(!audio.ready) return true
                    when(menu) {
                        0 -> startGame(1,e.deviceId)
                        1 -> { screen="JOIN";firstJoin=null;joining=true }
                        2 -> { screen="SCORES";titleTicks=0 }
                        3 -> screen="CONTROLS"
                        4 -> { screen="SETTINGS";menu=0 }
                        5 -> context.startActivity(Intent(context,ConnectionActivity::class.java))
                    }
                }
                if(back) (context as Activity).finish()
            }
            "JOIN" -> {
                if(back) { screen="TITLE";menu=0;joining=false }
                else if(flap) {
                    if(firstJoin==null) firstJoin=e.deviceId
                    else if(firstJoin!=e.deviceId) { assign(e.deviceId,1);startGame(2,firstJoin!!) }
                }
            }
            "PAUSE" -> {
                if(up) menu=(menu+3)%4
                if(down) menu=(menu+1)%4
                if(back || start) resumeGame()
                else if(flap) when(menu) {
                    0 -> resumeGame()
                    1 -> { audio.sequencer.reset();game.start(if(descriptors[1]!=null) 2 else 1);screen="PLAY";audio.resume() }
                    2 -> screen="CONTROLS"
                    3 -> { game.active=false;game.paused=false;screen="TITLE";menu=0;titleTicks=0;audio.sequencer.reset();audio.resume() }
                }
            }
            "CONTROLS" -> if(flap || back) { screen=if(game.active && !game.demo) "PAUSE" else "TITLE";menu=0 }
            "SCORES" -> {
                if(left) scorePage=(scorePage+7)%8
                if(right) scorePage=(scorePage+1)%8
                if(flap || back) { screen="TITLE";menu=0;titleTicks=0 }
            }
            "SETTINGS" -> {
                if(up) menu=(menu+3)%4
                if(down) menu=(menu+1)%4
                if(back || flap && menu==3) { screen="TITLE";menu=0 }
                else if(left || right || flap) {
                    when(menu) {
                        0 -> freePlay=!freePlay
                        1 -> overscan=(overscan+if(left) -0.025f else 0.025f).coerceIn(0.85f,1f)
                        2 -> { soundOn=!soundOn;audio.enabled=soundOn }
                    }
                    preferences.edit().putBoolean("freePlay",freePlay).putFloat("overscan",overscan).putBoolean("sound",soundOn).apply()
                }
            }
            "INITIALS" -> {
                if(left) initialCharacter=(initialCharacter+27)%28
                if(right) initialCharacter=(initialCharacter+1)%28
                if(flap) {
                    if(initialCharacter==27) { if(initialName.isNotEmpty()) initialName=initialName.dropLast(1) }
                    else {
                        initialName+=character()
                        if(initialName.length==3) {
                            highScores.submit(ScoreEntry(game.players[initialPlayer].score,initialName));nextInitials()
                        }
                    }
                }
            }
        }
        invalidate();return true
    }
    private fun startGame(count:Int,device:Int) {
        if(!freePlay && credits<count) { credits++;audio.sequencer.request("SNCRED",false);return }
        if(!freePlay) credits-=count
        if(count==1) { assignments.clear();descriptors[1]=null }
        assign(device,0);joining=false;waitingForScorePage=false;audio.sequencer.reset();game.start(count);screen="PLAY"
        audio.resume()
    }
    private fun assign(device:Int,player:Int) { assignments[device]=player;descriptors[player]=InputDevice.getDevice(device)!!.descriptor }
    fun motion(e:MotionEvent):Boolean {
        if(e.action!=MotionEvent.ACTION_MOVE || e.source and InputDevice.SOURCE_JOYSTICK!=InputDevice.SOURCE_JOYSTICK) return false
        val player=assignments[e.deviceId] ?: return true
        val c=game.controls[player]
        c.setHat(e.getAxisValue(MotionEvent.AXIS_HAT_X))
        c.setStick(e.getAxisValue(MotionEvent.AXIS_X),e.device.getMotionRange(MotionEvent.AXIS_X,e.source).flat)
        return true
    }
    fun disconnect(device:Int) {
        if(assignments.containsKey(device)) { assignments.remove(device);pause() }
    }
    fun reconnect(device:Int) {
        val descriptor=InputDevice.getDevice(device)!!.descriptor
        val player=descriptors.indexOf(descriptor)
        if(player>=0) assignments[device]=player
    }
    private fun pause() {
        if(screen=="PLAY") { game.paused=true;screen="PAUSE";menu=0 }
        game.controls.forEach { it.clearOnPauseOrDisconnect() };audio.pause()
    }
    private fun resumeGame() { game.controls.forEach { it.clearOnPauseOrDisconnect() };game.paused=false;screen="PLAY";previousNanos=0;accumulator=0;audio.resume() }
    fun suspendGame() { pause();hostActive=false;previousNanos=0;accumulator=0 }
    fun resumeHost() { hostActive=true;previousNanos=0;accumulator=0;if(!game.paused) audio.resume() }
    fun close() { Choreographer.getInstance().removeFrameCallback(this);audio.close() }
    private fun qualify() {
        initialsQueue=game.players.filter { highScores.qualifies(it.score) }.sortedByDescending { it.score }.map { it.index }.toMutableList()
        if(initialsQueue.isEmpty()) waitingForScorePage=true else nextInitials()
    }
    private fun nextInitials() {
        if(initialsQueue.isEmpty()) { screen="SCORES";titleTicks=0;game.active=false }
        else { initialPlayer=initialsQueue.removeAt(0);initialName="";initialCharacter=1;screen="INITIALS" }
    }
}
