package com.couchjoust.core

import kotlin.math.abs

enum class Category { PRIMARY, SECONDARY }

/** Mutable traversal preserves insertion after the current actor within this pass. */
open class Task(val category: Category) {
    var previous: Task? = null
    var next: Task? = null
    var delay = 1
    var removed = false
    open fun service(game: Game) {}
}

class Scheduler {
    val head = Task(Category.PRIMARY)
    var secondaryPredecessor: Task = head
    fun insert(anchor: Task, task: Task) {
        task.previous = anchor
        task.next = anchor.next
        anchor.next?.previous = task
        anchor.next = task
    }
    fun remove(task: Task) {
        if (secondaryPredecessor === task) secondaryPredecessor = task.previous!!
        task.previous!!.next = task.next
        task.next?.previous = task.previous
        task.removed = true
    }
    fun pass(game: Game, category: Category) {
        var predecessor = if (category == Category.PRIMARY) head else secondaryPredecessor
        while (predecessor.next != null) {
            val item = predecessor.next!!
            if (item.category == category) {
                item.delay = (item.delay - 1) and 255
                if (item.delay == 0) item.service(game)
            }
            if (!item.removed) predecessor = item
        }
        if (category == Category.SECONDARY) secondaryPredecessor = head
    }
    fun actors(): List<Actor> = buildList {
        var item = head.next
        while (item != null) {
            if (item is Actor) add(item)
            item = item.next
        }
    }
}

open class Actor(category: Category, var x: Int, var yQ: Int): Task(category) {
    var vy = 0
    var q = 0
    var remainder = 0
    var bumpX = 0
    var bumpY = 0
    var facing = 1
    var collidable = false
    var frame = 2
    var frameSet = "_BUZARD"
    val y get() = yQ shr 8
    val descriptor get() = Tables.frames.getValue(frameSet)[frame]
    open val profile get() = descriptor.profile
    open val top get() = y - 19
    open val collisionBottom get() = y
    open val right get() = x + 16
    open val lance get() = 0
    fun moveX(dx: Int) {
        val bump = GameMath.consumeHorizontalBump(bumpX)
        bumpX = bump.remaining
        x = GameMath.wrapMovedX(x + dx + bump.pixels)
    }
    fun horizontal() {
        val step = GameMath.integrateHorizontal(remainder, q)
        remainder = step.remainder
        moveX(step.pixels)
    }
}

class Player(val index: Int) {
    var score = 0L
    var reserves = 5
    var lives = 5
    var nextLife = 20_000L
    var chain = 0
    var deaths = 0
    var kills = 0
    var bird: Bird? = null
    var initials = ""
    var overMessage = 0
}

enum class BirdMode { INITIAL, QUEUED, GROUND, AIR, MATERIALIZE, PROTECTED, DEPART, RESPAWN, SINK, GONE }

class Bird(val owner: Int, var species: Int, x: Int, y: Int): Actor(
    if (owner >= 0) Category.PRIMARY else Category.SECONDARY, x, y * 256
) {
    var mode = BirdMode.INITIAL
    var stateTimer = 60
    var ground = 1
    var acceleration = 0
    var stride = 0
    var runningFrame = 0
    var flapHeld = false
    var flapTimer = 0
    var wingHold = 0
    var targetDelay = 90
    var ridden = true
    var pursuing = false
    var budget = 4
    var ai = "LINET"
    var aiTimer = 0
    var excursion = 0
    var line = 0
    var targetQ = 0
    var directionTimer = 0
    var lavaTimer = 0
    var feet = 0
    var replacement: Egg? = null
    var seekNear = false
    var transporter = -1
    var protectionPhase = 16
    var protectionColorPhase = 2
    var protectionColorWait = 16
    val protectionColors = intArrayOf(1,1,1)
    var sinkDeath = false
    var troll: Troll? = null
    override val lance get() = if (runningFrame == -1 && mode == BirdMode.GROUND) 2 else 0
    override val collisionBottom get() = if(mode==BirdMode.AIR || mode==BirdMode.DEPART) y-6 else y
    init { frameSet = when(owner) { 0 -> "_OSTRICH"; 1 -> "_STORK"; else -> "_BUZARD" } }
    fun setPose(logical: Int) { frame = logical * 2 + if (facing < 0) 1 else 0 }
    override fun service(game: Game) { game.serviceBird(this) }
}

class Egg(x: Int,y: Int,var species:Int,var budget:Int): Actor(Category.SECONDARY,x,y*256) {
    var state = 0 // falling, resting, hatching, standing, collected
    var bounced = false
    var timer = 0
    var hatchFrame = 0
    var bird: Bird? = null
    init { frameSet = "_EGGI"; frame = 0; collidable = true }
    override val top get() = y - if(state == 3 || hatchFrame >= 5) 11 else 6
    override val right get() = x + 6
    override fun service(game: Game) { game.serviceEgg(this) }
}

class Pterodactyl(x:Int,y:Int,val baiter:Boolean): Actor(Category.SECONDARY,x,y*256) {
    var countedBaiter=baiter
    var state = 0
    var timer = if(baiter) 138 else 60
    var interval = 120
    var targetX = 0
    var targetY = 0
    var attackTimer = 0
    var animation = 0
    var animationWait = 0
    var initialized = false
    var hidden = false
    var cooldown = 0
    var departing = false
    var deathAge = -1
    init { frameSet = "_IPTERO"; frame = 4; collidable = true }
    override val top get() = y - 10
    override val right get() = x + 27
    override val lance get() = -128
    override fun service(game:Game) { game.servicePtero(this) }
}

class Popup(x:Int,y:Int,val points:Int,var timer:Int=30): Actor(Category.SECONDARY,x,y*256) {
    override fun service(game:Game) { if(--timer == 0) game.scheduler.remove(this) else delay=1 }
}

class Poof(x:Int,y:Int): Actor(Category.PRIMARY,x,y*256) {
    var stage = 0
    init { frameSet="_POOF1";frame=0;this.x=x.coerceIn(4,288) }
    override fun service(game:Game) {
        if(stage==3) game.scheduler.remove(this)
        else { frameSet="_POOF${++stage}"; delay=intArrayOf(5,8,12)[stage-1] }
    }
}

class Troll(val victim:Bird): Actor(victim.category,victim.x-2,214*256) {
    var stage = 0
    var timer = 1
    var pull = 0
    var hold = 1800
    var gripping = false
    var tracking = false
    var retracting = false
    init { frameSet="_ILAVAT"; frame=0 }
    override fun service(game:Game) { game.serviceTroll(this) }
}

class TrollFront(val rear:Troll):Actor(rear.category,rear.x,rear.yQ) {
    init { frameSet="_ILAVAT";frame=5 }
    override fun service(game:Game) {
        val b=rear.victim
        if(!rear.gripping || rear.retracting || previous!==b || !b.ridden || b.mode!=BirdMode.AIR) {
            game.scheduler.remove(this);return
        }
        x=b.x-2;yQ=(b.y+10)*256;rear.x=x;rear.yQ=yQ
        if(--rear.hold<=0) rear.pull=minOf(1281,rear.pull+1)
        delay=1
    }
}

class Terrain {
    var landingX = Tables.landing_x_template.map { it or 32 }.toIntArray()
    var solidX = Tables.background_x.copyOf()
    var disabled = emptySet<String>()
    var burnLeft = -32
    var burnRight = 319
    var burning = false
    val flames=mutableListOf<Flame>()
    private var normalFlames=false
    var lavaY = 256
    var lavaTarget = 234
    var lavaWait = 20
    var bubbleStage = -1
    var bubbleX = 0
    var bubbleY = 0
    var bubbleHeight = 0
    var surfaceBubble = false
    var ticks = 0
    val destruction=mutableMapOf<String,Int>()
    val transporters = arrayOf(intArrayOf(113,80),intArrayOf(231,128),intArrayOf(23,137),intArrayOf(127,210))
    val occupied = BooleanArray(4)
    fun landing(x:Int,y:Int) = landingX[x+32] and Tables.landing_y[y]
    fun solid(x:Int,y:Int):Int = if(x in -32..319 && y in 0..255) solidX[x+32] and Tables.background_y[y] else 0
    fun support(x:Int,y:Int) = landing(x,y+1) and 127 != 0
    fun configure(record:WaveRecord,wave:Int) {
        for(name in record.disabled-disabled) destruction[name]=0
        for(name in disabled-record.disabled) destruction.remove(name)
        disabled = record.disabled
        solidX = Tables.background_x.copyOf()
        val solidBits = mapOf("CLIF1L" to 1,"CLIF1R" to 2,"CLIF2" to 4,"CLIF4" to 64)
        for(name in disabled) for(i in solidX.indices) solidX[i] = solidX[i] and solidBits.getValue(name).inv()
        rebuildLanding()
        if(wave>=3 && !burning) {
            burning=true
            flames.add(Flame(-32,1,1));flames.add(Flame(319,-1,7))
        }
        if(wave>=4 && !normalFlames) {
            normalFlames=true
            flames.add(Flame(20,0,0));flames.add(Flame(262,0,0).apply { frame=2;wait=4 })
        }
        lavaTarget=maxOf(224,239-wave*5)
    }
    fun rebuildLanding() {
        for(i in landingX.indices) {
            val x=i-32
            var v=Tables.landing_x_template[i]
            if(x in burnLeft..burnRight) v=v or 32
            if("CLIF1L" in disabled && x<100 || "CLIF1R" in disabled && x>200) v=v and 1.inv()
            if("CLIF2" in disabled) v=v and 2.inv()
            if("CLIF4" in disabled) v=v and 16.inv()
            landingX[i]=v
        }
    }
    fun tick(game:Game) {
        ticks++
        destruction.entries.forEach { it.setValue(it.value+1) }
        if(--lavaWait==0) {
            if(bubbleStage>=0) {
                bubbleStage++
                if(bubbleStage==(if(surfaceBubble) 3 else 2)) {
                    if(!surfaceBubble) bubbleHeight=(bubbleHeight+5) and 255
                    bubbleStage=-1
                    lavaWait=if(lavaY>lavaTarget) 20 else 1
                } else lavaWait=8
            } else if(lavaY>lavaTarget) {
                lavaY--;lavaWait=if(lavaY>lavaTarget) 20 else 1
            } else {
                val r=game.random.next()
                bubbleX=intArrayOf(0x1c,0x17,0x10,0x0d,0x06,0x04,0x74,0x78,0x7f,0x85,0x89,0x8c,0x15,0x90,0x83,0x05)[r.sevenBit and 15]*2
                surfaceBubble=r.bit==0
                bubbleY=lavaY+if(surfaceBubble) 0 else ((bubbleHeight and 15) or 2)
                bubbleStage=0;lavaWait=8
                if(!surfaceBubble) game.sound("SNBUB")
            }
        }
        flames.forEach { it.tick(this) }
    }
    fun landingLevel(mask:Int):Int = when {
        mask<8 -> when { mask==2 -> 80; mask>2 -> 128; else -> 68 }
        mask==8 -> 137
        mask<32 -> 162
        mask and 32 !=0 -> 210
        else -> -1
    }
    fun solidIndex(mask:Int):Int = when {
        mask and 128 !=0 -> 7
        mask<8 -> when { mask==2 -> 1; mask>2 -> 2; else -> 0 }
        mask==8 -> 3
        mask and 112 >32 -> 6
        mask and 112 <32 -> 4
        else -> 5
    }
}

/** Source LAVAB/LAVAF processes, including offscreen bridge consumption and sinking. */
class Flame(var position:Int,val direction:Int,val offset:Int) {
    var x=position
    var y=if(direction==0) 239 else 223
    var frame=0
    var wait=if(direction==0) 1 else 4
    var visible=false
    private var state=if(direction==0) 4 else 0
    fun tick(t:Terrain) {
        if(state==5 || --wait>0) return
        when(state) {
            0 -> { if(t.lavaY>224) wait=4 else { state=1;wait=1 } }
            1 -> {
                if(Tables.landing_x_template[position+32] and 32!=0) {
                    state=3;frame++;visible=true;wait=6
                } else {
                    position+=direction
                    if(direction>0) t.burnLeft=position else t.burnRight=position
                    t.rebuildLanding()
                    x=position+offset
                    if(x in -14..296) { frame++;visible=true;state=2;wait=6 }
                    else wait=1
                }
            }
            2 -> { frame++;state=1;wait=6 }
            3 -> {
                if(frame and 3==0) y++
                if(y>=239) { visible=false;state=5 }
                else { frame++;wait=6 }
            }
            4 -> { frame++;if(y>223) y--;visible=true;wait=6 }
        }
    }
}

object Collision {
    data class Hit(val rowA:Int,val rowB:Int)
    fun scan(a:Array<IntArray?>,ax:Int,ay:Int,b:Array<IntArray?>,bx:Int,by:Int):Hit? {
        var ia=maxOf(0,by-ay)
        var ib=maxOf(0,ay-by)
        var mode=0
        val dx=bx-ax
        while(ia<a.size && ib<b.size) {
            val ar=a[ia];val br=b[ib]
            if(ar==null || br==null) { if(mode!=0) return null }
            else when(mode) {
                0 -> when { ar[1]<br[0]+dx -> mode=1; ar[0]>br[1]+dx -> mode=2; else -> return Hit(ia,ib) }
                1 -> if(ar[1]>=br[0]+dx) return Hit(ia,ib)
                2 -> if(ar[0]<=br[1]+dx) return Hit(ia,ib)
            }
            ia++;ib++
        }
        return null
    }
    fun contact(a:Actor,b:Actor):Boolean {
        if(a.right<b.x || b.right<a.x || a.collisionBottom<b.top || b.collisionBottom<a.top) return false
        return scan(Tables.profiles.getValue(a.profile),a.x,a.top,Tables.profiles.getValue(b.profile),b.x,b.top)!=null
    }
    fun bounce(a:Actor,b:Actor) {
        val upper=if(a.y<=b.y) a else b
        val lower=if(upper===a) b else a
        upper.bumpY=-2;lower.bumpY=2
        if(upper.vy>0) upper.vy=(-upper.vy) shr 1
        if(lower.vy<0) lower.vy=(-lower.vy) shr 1
        if(a.x==b.x) return
        val left=if(a.x<=b.x) a else b
        val right=if(left===a) b else a
        if(left.q>0) left.q=-left.q+2
        if(right.q<0) right.q=-right.q-2
        right.bumpX=(-left.q+2) shr 1
        left.bumpX=(-(right.q+2)) shr 1
        left.facing=-1;right.facing=1
    }
    fun pteroBird(p:Pterodactyl,b:Bird) {
        if(b.top+b.collisionBottom<p.top+p.collisionBottom) {
            b.bumpY=-5
            if(b.vy>0) b.vy=(-b.vy) shr 1
        } else {
            b.bumpY=5
            if(b.vy<0) b.vy=(-b.vy) shr 1
        }
        if(b.x+b.right<p.x+p.right) {
            if(b.q>0) b.q=-b.q+2
            b.facing=-1
        } else {
            if(b.q<0) b.q=-b.q-2
            b.facing=1
        }
        b.bumpX=-p.q*2
    }
    fun mouth(player:Bird,p:Pterodactyl):Boolean {
        val relative=player.y+player.lance-p.y
        return (if(p.frame/2==2) abs(relative-8)<=3 else abs(relative-10)<=2) &&
            player.facing!=p.facing && player.facing==if(p.x-player.x<0) -1 else 1
    }
}
