package com.nyrds.pixeldungeon.abilities;

import com.nyrds.pixeldungeon.ai.Hunting;
import com.nyrds.pixeldungeon.ai.Passive;
import com.nyrds.pixeldungeon.game.GameLoop;
import com.watabou.pixeldungeon.Dungeon;
import com.watabou.pixeldungeon.actors.Actor;
import com.watabou.pixeldungeon.actors.hero.Hero;
import com.watabou.pixeldungeon.actors.mobs.Mob;
import com.watabou.pixeldungeon.levels.Level;
import com.watabou.pixeldungeon.levels.Terrain;
import com.watabou.pixeldungeon.scenes.GameScene;
import com.watabou.pixeldungeon.utils.GLog;
import java.util.Collections;

/** A launcher-only fixture. Normal dungeon generation never references this floor. */
public final class ProofPlaytest {
    private ProofPlaytest() { }

    public static void install() {
        if (!NormalAttackDamage.enabled()) throw new IllegalStateException("Proof must be enabled");
        ProofAbilities.resetTransientState();
        Arena arena = new Arena();
        // Reuse the current first-floor identity/properties, without changing dungeon data.
        arena.levelId = Dungeon.level.levelId;
        arena.create(21, 13);
        Dungeon.switchLevel(arena, arena.cell(5, 6), Collections.emptyList());
        Hero hero = Dungeon.hero;
        hero.ht(80);
        hero.hp(80);
        ProofAbilities.activate(hero);
        arena.rat(3, 6, 4, true);
        arena.rat(6, 6, 8, false);
        arena.rat(6, 3, 4, true);
        arena.rat(12, 3, 4, true);
        arena.rat(12, 6, 4, false);
        arena.rat(13, 6, 3, false);
        arena.rat(14, 6, 3, false);
        arena.rat(14, 7, 3, false);
        GameLoop.switchScene(GameScene.class);
        GLog.i("ATK 1 proof: walk away from pursuing rats to leave sparks. Hit to push.");
        GLog.i("Right room: lure the 4 HP rat onto a spark beside the 3 HP group for a death chain.");
        GLog.i("No stairs or random respawns. Relaunch to reset this practice floor.");
    }

    public static final class Arena extends Level {
        public Arena() { viewDistance = 12; }
        public int cell(int x, int y) { return x + y * getWidth(); }
        @Override protected boolean build() {
            for (int y = 1; y < getHeight()-1; y++) {
                for (int x = 1; x < getWidth()-1; x++) map[cell(x, y)] = Terrain.EMPTY;
            }
            for (int y = 1; y < getHeight()-1; y++) map[cell(9, y)] = Terrain.WALL;
            map[cell(9, 6)] = Terrain.EMPTY; // one-tile passage between rooms
            for (int x = 3; x <= 6; x++) map[cell(x, 9)] = Terrain.WALL;
            map[cell(7, 4)] = Terrain.WALL;
            map[cell(7, 5)] = Terrain.WALL;
            entrance = cell(5, 6);
            return true;
        }
        @Override protected void decorate() { }
        @Override protected void createMobs() { }
        @Override protected void createItems() { }
        @Override protected void createScript() { }
        @Override public boolean isBossLevel() { return true; } // skip random item generation
        @Override public Actor respawner() { return null; }
        private void rat(int x, int y, int hp, boolean pursuing) {
            Mob rat = new Mob("Rat");
            rat.setPos(cell(x, y));
            rat.ht(hp);
            rat.hp(hp);
            rat.setBaseDefenseSkill(0);
            rat.setState(pursuing ? new Hunting() : new Passive());
            if (pursuing) rat.setEnemy(Dungeon.hero);
            spawnMob(rat);
        }
    }
}
