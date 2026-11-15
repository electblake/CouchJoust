package com.couchjoust

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.KeyEvent
import android.view.InputDevice
import com.couchjoust.core.*
import java.io.File

/** Runs on a real Android TV image, exercising the Activity, input and renderer. */
class GameplayInstrumentation:Instrumentation() {
    private var probeArguments: Bundle? = null
    private lateinit var activity:MainActivity
    private val results=mutableListOf<String>()
    override fun onCreate(arguments:Bundle?) { probeArguments=arguments;super.onCreate(arguments);start() }
    override fun onStart() {
        if(probeArguments!!.containsKey("updates")) {
            UpdatesScenario(this).run(probeArguments!!)
            return
        }
        if(probeArguments!!.containsKey("connectionRole")) {
            ConnectionScenario(this).run(probeArguments!!)
            return
        }
        val monitor=addMonitor(MainActivity::class.java.name,null,false)
        targetContext.startActivity(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        activity=waitForMonitorWithTimeout(monitor,10_000) as MainActivity
        removeMonitor(monitor)
        Thread.sleep(500) // Allow the window to acquire focus before freezing the live clock.
        runOnMainSync { activity.gameView.hostActive=false;activity.gameView.screen="TITLE";activity.gameView.menu=0 }
        scenario("source marquee stripes and palette render around TV menu") {
            activity.gameView.titleTicks=400
            screenshot("00-title")
        }
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        scenario("title renders and center starts one player") {
            check(activity.gameView.screen=="PLAY") { "Start screen=${activity.gameView.screen}" }
            repeat(60) {activity.gameView.game.step()}
            screenshot("01-start")
        }
        scenario("remote direction and fresh flap; repeats do not flap") {
            val v=activity.gameView;val g=v.game;val b=g.players[0].bird!!
            down(KeyEvent.KEYCODE_DPAD_RIGHT)
            repeat(40) {g.step()}
            check(b.x>100)
            down(KeyEvent.KEYCODE_DPAD_CENTER);g.step();check(b.vy==-128)
            repeat(5) {g.step()};check(b.vy==-108)
            v.key(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,3))
            g.step();check(b.vy==-104)
            up(KeyEvent.KEYCODE_DPAD_CENTER);g.step();down(KeyEvent.KEYCODE_DPAD_CENTER);g.step()
            check(b.vy< -150)
            up(KeyEvent.KEYCODE_DPAD_RIGHT);up(KeyEvent.KEYCODE_DPAD_CENTER)
            screenshot("02-flight")
        }
        uiAutomation.executeShellCommand("input keyevent BACK").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        Thread.sleep(500) // Android completes its Back navigation dispatch asynchronously.
        waitForIdleSync()
        scenario("OS Back pauses without finishing and resume returns to gameplay") {
            val v=activity.gameView
            check(!activity.isFinishing) { "Back finished activity" }
            check(v.screen=="PAUSE") { "Back screen=${v.screen}" }
            check(v.game.paused) { "Back did not pause simulation" }
            screenshot("03-pause")
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(v.screen=="PLAY");check(!v.game.paused)
            check(!v.game.controls[0].sample().flap)
        }
        scenario("all ninety waves render and run with original tables") {
            val v=activity.gameView
            for(entry in 1..90) {
                v.game.start(2)
                v.game.entry=entry
                v.game.waveNumber=entry
                v.game.wavesEntered=entry
                v.game.enterWave(false)
                repeat(900) { t ->
                    v.game.controls.forEachIndexed { i,c -> c.flapKey=t%14<4;c.rightKey=(t/240+i)%2==0;c.leftKey=!c.rightKey }
                    v.game.step()
                }
                check(v.game.director.state>0)
                v.artwork.arena(v.game)
                if(entry in listOf(5,8,60,90)) screenshot("wave-${entry.toString().padStart(2,'0')}")
            }
        }
        scenario("all fourteen source demonstration lessons render") {
            val v=activity.gameView;v.game.start(2,true)
            v.screen="DEMO"
            val seen=mutableSetOf<Int>()
            while(!v.game.demonstration.complete) {
                v.game.step()
                if(v.game.demonstration.elapsed==20) {
                    seen.add(v.game.demonstration.stage)
                    screenshot("demo-${v.game.demonstration.stage.toString().padStart(2,'0')}")
                }
            }
            check(seen.size==14)
            v.screen="PLAY"
        }
        scenario("eggs hatch reserve and remount or remain collectible") {
            val g=activity.gameView.game;g.start(1)
            val e=Egg(120,210,1,3);e.state=1;e.timer=1;g.unresolved=1;g.scheduler.insert(g.director,e)
            g.serviceEgg(e);check(e.bird!=null);check(g.enemyCount==1)
            repeat(8) {g.serviceEgg(e)};check(e.state==3)
            g.collect(e,0);check(g.enemyCount==0);check(g.unresolved==0)
        }
        scenario("troll strict escape boundary and range double integration") {
            val g=activity.gameView.game;g.start(1)
            val b=g.players[0].bird!!;b.mode=BirdMode.AIR;b.yQ=205*256;b.x=20;b.flapHeld=true
            val t=Troll(b);b.troll=t;t.gripping=true;t.pull=6;g.troll=t
            b.vy=-390;g.moveBird(b,ControllerState.Sample(false,false,true));check(b.troll===t)
            b.vy=-391;g.moveBird(b,ControllerState.Sample(false,false,true));check(b.troll==null)
            check(g.players[0].score==50L)
            b.troll=t;t.gripping=true;b.x=100;b.yQ=205*256;b.vy=0
            g.moveBird(b,ControllerState.Sample(false,false,true));check(b.yQ==205*256+12)
        }
        scenario("two-player kill and survivor continues") {
            val g=activity.gameView.game;g.start(2);repeat(60) {g.step()}
            g.kill(g.players[1].bird!!,g.players[0].bird)
            check(g.players[0].score==2000L);check(g.players[1].score==50L);check(!g.gameOver)
            activity.gameView.artwork.arena(g);screenshot("04-two-player")
        }
        scenario("lifecycle pause and audio sequencing") {
            activity.gameView.suspendGame();check(activity.gameView.game.paused)
            activity.gameView.resumeHost();check(activity.gameView.game.paused)
            activity.gameView.hostActive=false
            val seq=activity.gameView.audio.sequencer
            seq.reset();check(seq.request("SNHIGH",false));check(!seq.request("SNPLWD",true));seq.reset()
        }
        scenario("focus restoration keeps the game paused and can resume") {
            val v=activity.gameView
            activity.onWindowFocusChanged(false);activity.onWindowFocusChanged(true)
            check(v.hostActive);check(v.game.paused)
            key(KeyEvent.KEYCODE_BACK);check(!v.game.paused);check(v.screen=="PLAY")
            v.hostActive=false
        }
        scenario("remote menus settings and original score pages") {
            val v=activity.gameView
            key(KeyEvent.KEYCODE_BACK)
            repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) };key(KeyEvent.KEYCODE_DPAD_CENTER)
            check(v.screen=="TITLE");screenshot("title")
            repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) };key(KeyEvent.KEYCODE_DPAD_CENTER)
            check(v.screen=="CONTROLS");screenshot("controls");key(KeyEvent.KEYCODE_BACK)
            repeat(4) { key(KeyEvent.KEYCODE_DPAD_DOWN) };key(KeyEvent.KEYCODE_DPAD_CENTER)
            check(v.screen=="SETTINGS")
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_LEFT)
            check(v.overscan==0.975f);key(KeyEvent.KEYCODE_DPAD_RIGHT)
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER)
            check(!v.soundOn);check(!v.audio.enabled)
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(v.soundOn);screenshot("settings")
            key(KeyEvent.KEYCODE_BACK)
            repeat(2) { key(KeyEvent.KEYCODE_DPAD_DOWN) };key(KeyEvent.KEYCODE_DPAD_CENTER)
            for(page in 0..7) { check(v.scorePage==page);screenshot("scores-$page");key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            key(KeyEvent.KEYCODE_BACK)
        }
        scenario("high scores preserve ties and persist new records") {
            val prefs=targetContext.getSharedPreferences("instrumentation-scores",0)
            prefs.edit().clear().commit()
            val scores=HighScores(prefs)
            val champion=scores.champion
            scores.submit(ScoreEntry(champion.score,"TIE"));check(scores.champion==champion)
            val entry=ScoreEntry(champion.score+1,"TVR")
            scores.submit(entry)
            val reloaded=HighScores(prefs)
            check(reloaded.champion==entry);check(reloaded.allTime.first()==entry)
            check(reloaded.today===scores.today)
            prefs.edit().clear().commit()
        }
        scenario("two device identities join move independently and reconnect") {
            val v=activity.gameView
            val second=InputDevice.getDeviceIds().first { it!=-1 }
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER)
            check(v.screen=="JOIN");key(KeyEvent.KEYCODE_DPAD_CENTER)
            fun deviceKey(code:Int,action:Int) {
                activity.dispatchKeyEvent(KeyEvent(0,0,action,code,0,0,second,0))
            }
            deviceKey(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN)
            deviceKey(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP)
            check(v.screen=="PLAY");check(v.assignments[second]==1)
            repeat(60) {v.game.step()}
            down(KeyEvent.KEYCODE_DPAD_RIGHT)
            deviceKey(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.ACTION_DOWN)
            check(v.game.controls[0].sample().direction==1)
            check(v.game.controls[1].sample().direction== -1)
            v.disconnect(second);check(v.screen=="PAUSE")
            check(v.game.controls.all { it.sample().direction==0 })
            v.reconnect(second);check(v.assignments[second]==1)
            key(KeyEvent.KEYCODE_BACK);check(v.screen=="PLAY")
            screenshot("two-devices")
        }
        scenario("game over enters initials and remote submits a new champion") {
            val v=activity.gameView
            v.game.start(1);v.screen="PLAY"
            val score=v.highScores.champion.score+100
            v.game.players[0].score=score
            v.game.gameOver=true;v.game.overTimer=87
            v.hostActive=true;v.previousNanos=1;v.accumulator=0
            v.doFrame(1+GameMath.TICK_NS);v.hostActive=false
            check(v.screen=="INITIALS");screenshot("initials")
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER)
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER)
            check(v.screen=="SCORES")
            check(v.highScores.champion==ScoreEntry(score,"ABC"))
            screenshot("new-champion")
        }
        val connectionMonitor=addMonitor(ConnectionActivity::class.java.name,null,false)
        scenario("connection prototype is reachable from TV title menu") {
            val v=activity.gameView;v.screen="TITLE";v.menu=5
            key(KeyEvent.KEYCODE_DPAD_CENTER)
        }
        val connectionActivity=waitForMonitorWithTimeout(connectionMonitor,10_000) as ConnectionActivity
        removeMonitor(connectionMonitor)
        runOnMainSync { connectionActivity.finish() }
        val updatesMonitor=addMonitor(UpdatesActivity::class.java.name,null,false)
        scenario("updates is reachable from TV title menu") {
            val v=activity.gameView;v.screen="TITLE";v.menu=6
            key(KeyEvent.KEYCODE_DPAD_CENTER)
        }
        val updatesActivity=waitForMonitorWithTimeout(updatesMonitor,10_000) as UpdatesActivity
        removeMonitor(updatesMonitor)
        runOnMainSync {
            check(!updatesActivity.downloadButton.isEnabled)
            check(updatesActivity.checkButton.isEnabled)
            updatesActivity.finish()
        }
        val result=Bundle()
        result.putString("stream",results.joinToString("\n",postfix="\n${results.size} virtual-device scenarios passed\n"))
        finish(Activity.RESULT_OK,result)
    }
    private fun scenario(name:String,action:()->Unit) {
        sendStatus(0,Bundle().apply { putString("stream","RUN $name\n") })
        runOnMainSync(action);waitForIdleSync();results.add("PASS $name")
    }
    private fun down(code:Int) { activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code)) }
    private fun up(code:Int) { activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code)) }
    private fun key(code:Int) {
        if(code==KeyEvent.KEYCODE_BACK) activity.onBackPressedDispatcher.onBackPressed()
        else { down(code);up(code) }
    }
    private fun screenshot(name:String) {
        val image=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
        activity.gameView.layout(0,0,1920,1080)
        activity.gameView.draw(Canvas(image))
        File(targetContext.getExternalFilesDir(null),"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
}
