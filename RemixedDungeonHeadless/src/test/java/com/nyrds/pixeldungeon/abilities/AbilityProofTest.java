package com.nyrds.pixeldungeon.abilities;

import com.nyrds.pixeldungeon.game.GameLoop;
import com.nyrds.pixeldungeon.game.GamePreferences;
import com.nyrds.pixeldungeon.ai.Passive;
import com.nyrds.pixeldungeon.mechanics.NamedEntityKind;
import com.nyrds.pixeldungeon.utils.GameControl;
import com.nyrds.platform.game.Game;
import com.nyrds.platform.game.RemixedDungeon;
import com.watabou.pixeldungeon.Dungeon;
import com.watabou.pixeldungeon.actors.Char;
import com.watabou.pixeldungeon.actors.Actor;
import com.watabou.pixeldungeon.actors.buffs.Buff;
import com.watabou.pixeldungeon.actors.hero.Belongings;
import com.watabou.pixeldungeon.actors.hero.Hero;
import com.watabou.pixeldungeon.actors.mobs.Mob;
import com.watabou.pixeldungeon.items.weapon.melee.ShortSword;
import com.watabou.pixeldungeon.levels.Level;
import com.watabou.pixeldungeon.levels.Terrain;
import com.watabou.pixeldungeon.scenes.GameScene;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.Arrays;
import org.luaj.vm2.LuaTable;

/** Real no-GL game integration checks, with deterministic fixtures, not mock damage. */
public final class AbilityProofTest {
    private static int passed;

    public static void main(String[] args) {
        try {
            GameLoop.headless = true;
            GamePreferences.noSound = true;
            GamePreferences.activeMod("Remixed");
            GamePreferences.intro(false);
            RemixedDungeon game = new RemixedDungeon();
            game.create();
            game.startLoop();
            await(() -> GameLoop.instance().framesSinceInit >= 3);
            onLoop(() -> GameControl.startNewGame("WARRIOR", 0, true));
            await(() -> Dungeon.level != null && Dungeon.hero.isAlive());
            await(() -> GameLoop.scene() instanceof GameScene);
            onLoop(AbilityProofTest::normalAttacks);
            if (args.length == 0 || !args[0].equals("--atk-only")) {
                onLoop(AbilityProofTest::sparks);
                onLoop(AbilityProofTest::rejection);
                onLoop(AbilityProofTest::aftershock);
                onLoop(AbilityProofTest::depthLimit);
                onLoop(AbilityProofTest::activationLimit);
                onLoop(AbilityProofTest::longRun);
                Object previousScene = GameLoop.scene();
                onLoop(ProofPlaytest::install);
                await(() -> Dungeon.level instanceof ProofPlaytest.Arena && GameLoop.scene() != previousScene
                        && GameLoop.scene() instanceof GameScene);
                onLoop(AbilityProofTest::playtestFloor);
            }
            System.out.println("ABILITY PROOF PASS: " + passed + " checks");
            Game.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            Game.exit(1);
        }
    }

    private static void normalAttacks() {
        Hero hero = Dungeon.hero;
        Level level = Dungeon.level;
        int cell = hero.getPos() + 1;
        level.map[cell] = Terrain.EMPTY;
        level.buildFlagMaps();
        HitTarget target = new HitTarget();
        target.setPos(cell);
        target.ht(10000);
        target.hp(10000);
        level.spawnMob(target);
        hero.invisible = 1; // existing hit() guarantee; no random misses in fixtures
        hero.setBaseAttackSkill(1000);
        check("ordinary hit is one", hit(hero, target) == 1);
        hero.STR(100);
        hero.lvl(40);
        ShortSword weapon = new ShortSword();
        for (int i = 0; i < 10; i++) weapon.upgrade();
        hero.getBelongings().setItemForSlot(weapon, Belongings.Slot.WEAPON);
        for (int i = 0; i < 20; i++) {
            check("STR/level/weapon/critical and target amplification remain one", hit(hero, target) == 1);
        }
        int before = target.hp();
        target.damage(7, hero); // same source, outside ordinary-hit scope
        check("non-ordinary hero damage remains above one", before - target.hp() == 700);
        System.setProperty(NormalAttackDamage.PROPERTY, "false");
        check("base game remains uncapped when proof is disabled", hit(hero, target) > 1);
        System.setProperty(NormalAttackDamage.PROPERTY, "true");
        target.destroy();
    }

    private static ProofAbilities fixture() {
        ProofAbilities.resetTransientState();
        Level level = Dungeon.level;
        for (Mob mob : new ArrayList<>(level.mobs)) mob.destroy();
        for (Buff buff : new ArrayList<>(Dungeon.hero.buffs())) buff.detach();
        for (Actor actor : new ArrayList<>(Actor.all())) {
            if (actor != Dungeon.hero) Actor.remove(actor);
        }
        // Fixed arena and zero-evasion fixtures make outcomes reproducible despite
        // the engine's unseedable Math.random(). We do not modify global RNG.
        Arrays.fill(level.map, Terrain.WALL);
        for (int y = 1; y < level.getHeight()-1; y++) {
            for (int x = 1; x < level.getWidth()-1; x++) level.map[cell(x, y)] = Terrain.EMPTY;
        }
        level.buildFlagMaps();
        Hero hero = Dungeon.hero;
        hero.ht(10000);
        hero.hp(10000);
        hero.STR(11);
        hero.lvl(1);
        hero.invisible = 0;
        hero.getBelongings().setItemForSlot(new ShortSword(), Belongings.Slot.WEAPON);
        hero.setPos(cell(5, 5));
        Actor.init(level);
        hero.setPos(cell(5, 5));
        ProofAbilities bridge = ProofAbilities.activate(hero);
        LuaTable metadata = bridge.metadata();
        check("three simultaneous owned abilities and metadata", metadata.length() == 3
                && bridge.owns("ProofSparks") && bridge.owns("ProofRejection") && bridge.owns("ProofAftershock"));
        for (int i = 1; i <= metadata.length(); i++) {
            check("system tags present", metadata.get(i).get("tags").length() >= 3);
        }
        return bridge;
    }

    private static int cell(int x, int y) { return x + y * Dungeon.level.getWidth(); }

    private static Mob rat(int x, int y, int hp) {
        Mob mob = new Mob("Rat");
        mob.setPos(cell(x, y));
        mob.ht(hp);
        mob.hp(hp);
        mob.setBaseDefenseSkill(0);
        mob.lvl(0);
        mob.setState(new Passive());
        Dungeon.level.spawnMob(mob);
        return mob;
    }

    private static void sparks() {
        ProofAbilities bridge = fixture();
        Hero hero = Dungeon.hero;
        hero.move(cell(6, 5));
        check("spark appears on previous tile only", bridge.hasSpark(cell(5, 5)) && !bridge.hasSpark(cell(6, 5)));
        Mob follower = rat(4, 5, 20);
        follower.move(cell(5, 5));
        check("spark deals four special damage", follower.hp() == 16 && !bridge.hasSpark(cell(5, 5)));
        hero.move(cell(7, 5));
        for (int i = 0; i < 4; i++) tick();
        check("spark expires in game turns", !bridge.hasSpark(cell(6, 5)));
        check("Lua bridge has no errors", bridge.failureCount() == 0);
    }

    private static void rejection() {
        ProofAbilities bridge = fixture();
        Hero hero = Dungeon.hero;
        Mob target = rat(6, 5, 30);
        check("ordinary hit remains one with all abilities", hit(hero, target) == 1);
        check("landed hit pushes one tile and updates occupancy", target.getPos() == cell(7, 5)
                && Actor.findChar(cell(6, 5)) == null && Actor.findChar(cell(7, 5)) == target);
        target.setPos(cell(6, 5));
        Dungeon.level.map[cell(7, 5)] = Terrain.WALL;
        Dungeon.level.buildFlagMaps();
        hit(hero, target);
        check("wall blocks knockback", target.getPos() == cell(6, 5));
        Dungeon.level.map[cell(7, 5)] = Terrain.EMPTY;
        Dungeon.level.buildFlagMaps();
        Mob blocker = rat(7, 5, 20);
        hit(hero, target);
        check("occupied tile blocks knockback", target.getPos() == cell(6, 5) && blocker.getPos() == cell(7, 5));
        blocker.destroy();
        // Move -> footprint -> attack -> push onto footprint -> special damage.
        hero.setPos(cell(7, 5));
        hero.move(cell(7, 6));
        hero.setPos(cell(5, 5));
        int before = target.hp();
        hit(hero, target);
        check("knockback into a spark combines 1 ordinary plus 4 special", before - target.hp() == 5);
        check("combined position effects have no errors", bridge.failureCount() == 0);
    }

    private static void aftershock() {
        ProofAbilities bridge = fixture();
        Mob first = rat(9, 5, 3);
        Mob second = rat(10, 5, 3);
        Mob third = rat(11, 5, 20);
        Mob outside = rat(15, 5, 20);
        first.damage(4, Dungeon.hero);
        check("death aftershock kills a second enemy", !first.isAlive() && !second.isAlive());
        check("secondary death emits another aftershock", third.hp() == 17);
        check("AoE respects radius", outside.hp() == 20);
        check("death chain has no errors", bridge.failureCount() == 0);
    }

    private static void depthLimit() {
        ProofAbilities bridge = fixture();
        Mob first = null;
        int count = Math.min(28, Dungeon.level.getWidth()-4);
        check("fixture exceeds depth limit", count > ProofAbilities.MAX_CHAIN_DEPTH + 2);
        for (int x = 2; x < count + 2; x++) {
            Mob mob = rat(x, 8, 3);
            if (first == null) first = mob;
        }
        first.damage(4, Dungeon.hero);
        check("long death chain is truncated without recursion", bridge.suppressedEvents() > 0
                && bridge.lastMaximumDepth() <= ProofAbilities.MAX_CHAIN_DEPTH && !Dungeon.level.mobs.isEmpty());
        check("depth-limited chain leaves healthy game state", Dungeon.hero.isAlive() && bridge.failureCount() == 0);
        float before = Actor.localTime();
        tick();
        check("turn advances after depth limit", Actor.localTime() >= before + 1 && Dungeon.hero.isAlive());
    }

    private static void activationLimit() {
        ProofAbilities bridge = fixture();
        Mob first = null;
        for (int y = 2; y <= 16; y++) {
            for (int x = 2; x <= 16; x++) {
                Mob mob = rat(x, y, 3);
                if (x == 9 && y == 9) first = mob;
            }
        }
        first.damage(4, Dungeon.hero);
        check("wide chain reaches activation budget", bridge.lastActivations() == ProofAbilities.MAX_ACTIVATIONS
                && bridge.suppressedEvents() > 0 && bridge.lastMaximumDepth() < ProofAbilities.MAX_CHAIN_DEPTH);
        check("budget-capped chain does not crash", bridge.failureCount() == 0);
        float before = Actor.localTime();
        tick();
        check("turn advances after activation limit", Actor.localTime() >= before + 1 && Dungeon.hero.isAlive());
    }

    private static void tick() {
        // Complete render-only motion just as its normal tween callback would.
        // Bulk headless checks must not enqueue 1000 unfinished animations.
        for (Actor actor : new ArrayList<>(Actor.all())) {
            if (actor instanceof Char) ((Char) actor).getSprite().completeForce();
        }
        Dungeon.hero.spendAndNext(1);
        Actor.processTurnBased(0.1f);
    }

    private static void longRun() {
        ProofAbilities bridge = fixture();
        float before = Actor.localTime();
        for (int i = 0; i < 1000; i++) {
            Dungeon.hero.move(cell(i % 2 == 0 ? 6 : 5, 5));
            tick();
        }
        check("1000 movement turns complete (time " + before + " -> " + Actor.localTime() + ")",
                Actor.localTime() >= before + 999 && Dungeon.hero.isAlive());
        check("temporary tile state remains bounded", bridge.sparkCount() <= 2);
        check("1000 turns with three abilities have no errors", bridge.failureCount() == 0);
    }

    private static int hit(Hero hero, Mob target) {
        int before = target.hp();
        if (!hero.attack(target)) throw new AssertionError("Fixture attack missed");
        return before - target.hp();
    }

    private static void playtestFloor() {
        ProofAbilities bridge = ProofAbilities.activate(Dungeon.hero);
        check("playable fixture has eight enemies and three abilities", Dungeon.level.mobs.size() == 8
                && bridge.owns("ProofSparks") && bridge.owns("ProofRejection") && bridge.owns("ProofAftershock"));
        check("playable fixture has walls and a single-tile passage", !Dungeon.level.passable[cell(9, 5)]
                && Dungeon.level.passable[cell(9, 6)] && !Dungeon.level.passable[cell(9, 7)]);
        Dungeon.hero.move(cell(5, 7));
        tick();
        check("playable fixture advances real actor turns", Dungeon.hero.isAlive() && bridge.hasSpark(cell(5, 6)));
        check("playable fixture scripts remain healthy", bridge.failureCount() == 0);
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }

    private static void onLoop(Runnable task) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        if (!GameLoop.pushUiTaskAndWait(() -> {
            try { task.run(); } catch (Throwable failure) { error.set(failure); }
        })) throw new AssertionError("Game loop did not complete task within five seconds");
        if (error.get() != null) throw new AssertionError("Game-loop check failed", error.get());
    }

    private static void await(java.util.function.BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Game startup timed out");
            Thread.sleep(25);
        }
    }

    private static final class HitTarget extends Mob {
        HitTarget() { super("Rat"); }
        @Override public int defenseSkill(Char attacker) { return 0; }
        @Override public int defenseProc(Char attacker, int damage) { return damage * 10; }
        @Override public void damage(int damage, NamedEntityKind source) {
            super.damage(damage * 100, source);
        }
    }
}
