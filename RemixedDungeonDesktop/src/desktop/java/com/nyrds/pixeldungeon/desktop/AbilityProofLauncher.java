package com.nyrds.pixeldungeon.desktop;

import com.nyrds.pixeldungeon.abilities.NormalAttackDamage;
import com.nyrds.pixeldungeon.abilities.ProofPlaytest;
import com.nyrds.pixeldungeon.game.GameLoop;
import com.nyrds.pixeldungeon.game.GamePreferences;
import com.nyrds.pixeldungeon.utils.GameControl;
import com.nyrds.platform.app.RemixedDungeonApp;
import com.watabou.pixeldungeon.Dungeon;
import com.watabou.pixeldungeon.scenes.GameScene;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Dedicated opt-in start; uses the existing desktop renderer, input and game loop. */
public final class AbilityProofLauncher {
    public static void main(String[] args) {
        System.setProperty(NormalAttackDamage.PROPERTY, "true");
        Thread bootstrap = new Thread(() -> {
            try {
                await(() -> GameLoop.instance() != null && GameLoop.instance().framesSinceInit >= 3);
                onLoop(() -> {
                    GamePreferences.activeMod("Remixed");
                    GamePreferences.intro(false);
                    GameControl.startNewGame("WARRIOR", 0, true);
                });
                await(() -> Dungeon.level != null && GameLoop.scene() instanceof GameScene);
                Object previousScene = GameLoop.scene();
                onLoop(ProofPlaytest::install);
                await(() -> GameLoop.scene() != previousScene && GameLoop.scene() instanceof GameScene);
                onLoop(() -> {
                    com.watabou.pixeldungeon.utils.GLog.i("ATK 1 / 3 abilities. Move to leave 4-damage sparks; hit to push.");
                    com.watabou.pixeldungeon.utils.GLog.i("Enemy deaths blast nearby enemies for 3. Try the group in the right room.");
                });
                System.out.println("ABILITY PROOF PLAYTEST READY: 3 abilities, 8 rats, 21x13 floor");
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(1);
            }
        }, "ability-proof-bootstrap");
        bootstrap.setDaemon(true);
        bootstrap.start();
        RemixedDungeonApp.main(args);
    }

    private static void onLoop(Runnable task) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        if (!GameLoop.pushUiTaskAndWait(() -> {
            try { task.run(); } catch (Throwable failure) { error.set(failure); }
        })) throw new IllegalStateException("Game loop did not complete proof startup");
        if (error.get() != null) throw new IllegalStateException("Proof startup failed", error.get());
    }

    private static void await(BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Proof startup timed out");
            Thread.sleep(25);
        }
    }
}
