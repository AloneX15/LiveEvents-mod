package com.takumistudios.liveevents.core.plan;

import com.takumistudios.liveevents.core.random.DeterministicRandom;

import java.util.ArrayList;
import java.util.List;

/**
 * Impactos de una lluvia de meteoritos, generados con la semilla del módulo. El servidor (cráteres, daño) y el cliente
 * (estela, aviso en el suelo, onda) obtienen exactamente la misma lista sin intercambiar datos.
 */
public final class MeteorPlan {
    /**
     * @param tick          momento del impacto (ticks del evento)
     * @param dx            desplazamiento X desde el centro
     * @param dz            desplazamiento Z desde el centro
     * @param craterRadius  radio del cráter
     * @param entryAzimuth  dirección de la que viene (grados), para dibujar la estela
     * @param entryAngle    inclinación de la trayectoria (grados sobre el horizonte)
     * @param big           bólido grande (primer impacto si first_big)
     */
    public record Impact(int index, double tick, double dx, double dz, double craterRadius, double entryAzimuth,
                         double entryAngle, boolean big) {
    }

    private MeteorPlan() {
    }

    public static List<Impact> generate(long seed, int count, double radius, double fromTicks, double toTicks,
                                        double craterMin, double craterMax, boolean firstBig) {
        int n = Math.max(1, Math.min(count, 400));
        DeterministicRandom random = new DeterministicRandom(seed);
        double span = Math.max(1, toTicks - fromTicks);
        double baseAzimuth = random.range(0, 360); // todos llegan más o menos del mismo radiante
        List<Impact> impacts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double slot = (i + random.range(0.1, 0.9)) / n;
            double tick = fromTicks + span * slot;
            double distance = radius * Math.sqrt(random.nextDouble()); // uniforme en el disco
            double angle = random.range(0, Math.PI * 2);
            double size = MeteorEffects.sizeMultiplier(new DeterministicRandom(DeterministicRandom.derive(seed, i)).nextDouble());
            boolean big = (firstBig && i == 0) || size >= 1.5;
            double crater = Math.clamp(firstBig && i == 0 ? craterMax * 1.4
                    : random.range(craterMin, craterMax) * size, 0.5, 48);
            if (!(firstBig && i==0) && MeteorEffects.giantRoll(seed,i)) { crater=100;big=true; }
            impacts.add(new Impact(i, tick, Math.cos(angle) * distance, Math.sin(angle) * distance, crater,
                    baseAzimuth + random.range(-25, 25), random.range(25, 55), big));
        }
        return impacts;
    }
}
