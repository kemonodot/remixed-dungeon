package com.nyrds.pixeldungeon.abilities;

import com.nyrds.LuaInterface;
import com.nyrds.pixeldungeon.game.GameLoop;
import com.nyrds.pixeldungeon.mechanics.LuaScript;
import com.nyrds.pixeldungeon.mechanics.NamedEntityKind;
import com.nyrds.pixeldungeon.mechanics.buffs.CustomBuff;
import com.watabou.noosa.particles.Emitter;
import com.watabou.pixeldungeon.Dungeon;
import com.watabou.pixeldungeon.actors.Actor;
import com.watabou.pixeldungeon.actors.Char;
import com.watabou.pixeldungeon.actors.hero.Hero;
import com.watabou.pixeldungeon.actors.mobs.Mob;
import com.watabou.pixeldungeon.effects.CellEmitter;
import com.watabou.pixeldungeon.effects.particles.SparkParticle;
import com.watabou.pixeldungeon.levels.Level;
import com.watabou.pixeldungeon.scenes.GameScene;
import com.watabou.pixeldungeon.utils.GLog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaTable;

/** Small opt-in event bridge, not a new game mode or a general ability framework. */
@LuaInterface
public final class ProofAbilities {
    public static final int MAX_CHAIN_DEPTH = 16;
    public static final int MAX_ACTIVATIONS = 128;
    public static final int MAX_QUEUED_EVENTS = 256;
    private static ProofAbilities active;

    private final Hero hero;
    private final Level level;
    private final LuaScript registry = new LuaScript("scripts/abilities/proof/registry", this);
    private final ArrayDeque<Event> queue = new ArrayDeque<>();
    private final Set<Char> deathSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Integer, Spark> sparks = new HashMap<>();
    private final Clock clock = new Clock();
    private boolean processing;
    private boolean faulted;
    private int depth;
    private int activations;
    private int processed;
    private int suppressed;
    private int failures;
    private int maximumDepth;

    private ProofAbilities(Hero hero, Level level) {
        this.hero = hero;
        this.level = level;
        Actor.add(clock);
    }

    public static ProofAbilities activate(Hero hero) {
        if (!NormalAttackDamage.enabled()) throw new IllegalStateException("Ability proof is not enabled");
        ProofAbilities bridge = session();
        if (bridge == null || bridge.hero != hero) throw new IllegalStateException("Hero is not on the active level");
        LuaTable definitions = bridge.metadata();
        for (int i = 1; i <= definitions.length(); i++) {
            String id = definitions.get(i).get("id").checkjstring();
            if (hero.buff(id) == null && !new CustomBuff(id).attachTo(hero)) {
                throw new IllegalStateException("Could not attach " + id);
            }
        }
        return bridge;
    }

    private static ProofAbilities session() {
        Hero hero = Dungeon.hero;
        if (!NormalAttackDamage.enabled() || hero == null || !hero.isAlive() || Dungeon.level == null) return null;
        if (active == null || active.hero != hero || active.level != Dungeon.level) {
            if (active != null) active.dispose();
            active = new ProofAbilities(hero, Dungeon.level);
        }
        return active;
    }

    public static void onMoved(Char actor, int from, int to) {
        ProofAbilities bridge = session();
        if (bridge == null || from == to || !bridge.level.cellValid(from) || !bridge.level.cellValid(to)) return;
        // Teleports do not create a path of fictitious footprints.
        if (bridge.level.adjacent(from, to)) bridge.post(new Event("MOVE", actor, null, from, to, false));
        bridge.post(new Event("ENTER", actor, null, from, to, false));
    }

    public static void onHit(Char actor, Char target) {
        ProofAbilities bridge = session();
        if (bridge != null && actor == bridge.hero) bridge.post(new Event("HIT", actor, target, actor.getPos(), target.getPos(), false));
    }

    public static boolean enemyForDeath(Char actor) {
        ProofAbilities bridge = session();
        return bridge != null && bridge.isEnemy(actor);
    }

    public static void onDeath(Char actor, boolean enemy) {
        ProofAbilities bridge = session();
        if (bridge != null && enemy && !actor.isAlive()) {
            bridge.post(new Event("DEATH", actor, null, actor.getPos(), actor.getPos(), true));
        }
    }

    private void post(Event event) {
        if (faulted) return;
        boolean root = !processing;
        if (root) {
            depth = -1;
            activations = 0;
            processed = 0;
            maximumDepth = 0;
            deathSeen.clear();
        }
        event.depth = depth + 1;
        if (event.depth > MAX_CHAIN_DEPTH || queue.size() >= MAX_QUEUED_EVENTS
                || (event.enemyDeath && !deathSeen.add(event.actor))) {
            suppressed++;
            return;
        }
        queue.addLast(event);
        if (!root) return;
        processing = true;
        try {
            while (!queue.isEmpty()) {
                // Covers events that do not trigger an ability as well.
                if (++processed > MAX_QUEUED_EVENTS || activations >= MAX_ACTIVATIONS) {
                    suppressed += queue.size();
                    queue.clear();
                    break;
                }
                Event next = queue.removeFirst();
                depth = next.depth;
                maximumDepth = Math.max(maximumDepth, depth);
                expireSparks();
                if (next.type.equals("ENTER")) enterSpark(next.actor, next.cell);
                else registry.run("dispatch", next);
            }
        } catch (RuntimeException failure) {
            // A broken ability stops this proof session instead of wedging the loop.
            failures++;
            faulted = true;
            GLog.w("Ability proof disabled after script error: %s", failure.toString());
            failure.printStackTrace();
        } finally {
            processing = false;
            queue.clear();
            deathSeen.clear();
            depth = -1;
        }
    }

    public boolean owns(String id) { return hero.buff(id) != null; }
    public boolean isHero(Char actor) { return actor == hero; }
    public boolean isEnemy(Char actor) {
        return actor instanceof Mob && actor.level() == level && !actor.friendly(hero) && !((Mob) actor).isPet();
    }

    public boolean reserveActivation(String id) {
        if (activations >= MAX_ACTIVATIONS || faulted) {
            suppressed++;
            return false;
        }
        activations++;
        return true;
    }

    public void leaveSpark(int cell, int damage, int lifetime) {
        removeSpark(cell);
        Spark spark = new Spark(damage, Actor.localTime() + lifetime);
        if (!GameLoop.headless && GameScene.isSceneReady()) {
            spark.emitter = CellEmitter.get(cell);
            spark.emitter.start(SparkParticle.FACTORY, 0.15f, 0);
        }
        sparks.put(cell, spark);
    }

    private void enterSpark(Char actor, int cell) {
        Spark spark = sparks.get(cell);
        if (spark != null && actor.isAlive() && isEnemy(actor) && reserveActivation("ProofSparks")) {
            removeSpark(cell); // consume before damage/death can enqueue further events
            actor.damage(spark.damage, new Cause("ProofSparks"));
            GLog.i("Footprint Sparks: %d special damage", spark.damage);
        }
    }

    public void knockBack(Char target) {
        if (!target.isAlive() || !isEnemy(target)) return;
        int width = level.getWidth();
        int x = target.getPos() % width;
        int y = target.getPos() / width;
        int nx = x + Integer.signum(x - hero.getPos() % width);
        int ny = y + Integer.signum(y - hero.getPos() / width);
        if (nx < 0 || nx >= width || ny < 0 || ny >= level.getHeight()) return;
        int to = nx + ny * width;
        if (!level.passable[to] || level.pit[to] || Actor.findChar(to) != null) return;
        target.placeTo(to); // occupancy, terrain pressure and ENTER share the normal path
        if (target.isAlive()) target.getSprite().place(to);
        GLog.i("Rejection: pushed one tile");
    }

    public void blast(int cell, int damage, int radius) {
        if (!GameLoop.headless && GameScene.isSceneReady()) {
            CellEmitter.get(cell).burst(SparkParticle.FACTORY, 16);
        }
        // Snapshot: deaths remove mobs from the live collection.
        for (Mob mob : new ArrayList<>(level.mobs)) {
            if (mob.isAlive() && isEnemy(mob) && level.distance(cell, mob.getPos()) <= radius) {
                mob.damage(damage, new Cause("ProofAftershock"));
            }
        }
        GLog.i("Death Aftershock: %d special damage", damage);
    }

    public LuaTable metadata() { return registry.run("metadata").checktable(); }
    public boolean hasSpark(int cell) { expireSparks(); return sparks.containsKey(cell); }
    public int sparkCount() { expireSparks(); return sparks.size(); }
    public int lastActivations() { return activations; }
    public int lastMaximumDepth() { return maximumDepth; }
    public int suppressedEvents() { return suppressed; }
    public int failureCount() { return failures; }

    static void resetTransientState() {
        if (active != null) active.dispose();
        active = null;
    }

    private void expireSparks() {
        for (int cell : new ArrayList<>(sparks.keySet())) {
            if (sparks.get(cell).expires <= Actor.localTime()) removeSpark(cell);
        }
    }

    private void removeSpark(int cell) {
        Spark spark = sparks.remove(cell);
        if (spark != null && spark.emitter != null) spark.emitter.on = false;
    }

    private void dispose() {
        for (int cell : new ArrayList<>(sparks.keySet())) removeSpark(cell);
        Actor.remove(clock);
        queue.clear();
    }

    private final class Clock extends Actor {
        @Override protected void act() {
            if (active != ProofAbilities.this || Dungeon.level != level || !hero.isAlive()) {
                dispose();
                return;
            }
            expireSparks();
            spend(1);
        }
        @Override public String getEntityKind() { return "ProofSparkClock"; }
        @Override public String name() { return getEntityKind(); }
    }

    private static final class Spark {
        final int damage;
        final float expires;
        Emitter emitter;
        Spark(int damage, float expires) { this.damage = damage; this.expires = expires; }
    }

    private static final class Cause implements NamedEntityKind {
        private final String id;
        Cause(String id) { this.id = id; }
        @Override public String getEntityKind() { return id; }
        @Override public String name() { return id; }
    }

    @LuaInterface
    public static final class Event {
        private final String type;
        private final Char actor;
        private final Char target;
        private final int from;
        private final int cell;
        private final boolean enemyDeath;
        private int depth;
        Event(String type, Char actor, Char target, int from, int cell, boolean enemyDeath) {
            this.type = type; this.actor = actor; this.target = target;
            this.from = from; this.cell = cell; this.enemyDeath = enemyDeath;
        }
        public String getType() { return type; }
        public Char getActor() { return actor; }
        public Char getTarget() { return target; }
        public int getFrom() { return from; }
        public int getCell() { return cell; }
        public boolean isEnemyDeath() { return enemyDeath; }
    }
}
