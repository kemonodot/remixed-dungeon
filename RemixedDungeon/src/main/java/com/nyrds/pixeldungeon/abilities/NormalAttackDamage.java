package com.nyrds.pixeldungeon.abilities;

import com.nyrds.pixeldungeon.mechanics.NamedEntityKind;
import com.watabou.pixeldungeon.actors.Char;
import com.watabou.pixeldungeon.actors.hero.Hero;

/** Opt-in proof: cap the ordinary hit, never all damage attributed to the hero. */
public final class NormalAttackDamage {
    public static final String PROPERTY = "rpd.abilityProof";
    private static final ThreadLocal<Hit> current = new ThreadLocal<>();

    private NormalAttackDamage() { }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static int apply(Char attacker, Char target, int damage) {
        if (!enabled() || !(attacker instanceof Hero)) {
            target.damage(damage, attacker);
            return damage;
        }
        Hit previous = current.get();
        current.set(new Hit(attacker, target));
        try {
            target.damage(1, attacker);
            return 1;
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
        }
    }

    public static int limit(Char target, NamedEntityKind source, int damage) {
        Hit hit = current.get();
        // Preserve immunity/absorption (zero), but reject amplification of a hit.
        return hit != null && hit.target == target && hit.attacker == source
                ? Math.min(1, Math.max(0, damage)) : damage;
    }

    private static final class Hit {
        final Char attacker;
        final Char target;
        Hit(Char attacker, Char target) {
            this.attacker = attacker;
            this.target = target;
        }
    }
}
