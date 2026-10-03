package com.takumistudios.liveevents.client.visual;

/**
 * Estado visual de un fotograma: capas de pantalla mezcladas por el {@code LayerMixer} (§7.12), objetos anclados a la
 * bóveda y líneas en el mundo. Se reutiliza en cada fotograma: ningún objeto se crea durante el render.
 */
public final class FrameState {
    /** Sprites disponibles para los objetos del cielo (texturas de assets/liveevents/textures/effect). */
    public enum Sprite {
        GLOW, CORE, RING, FLARE, CLOUD, FILAMENTS, AURORA, BEAM, LINE
    }

    /** Objeto anclado a la bóveda: dirección (acimut/altura) y tamaño angular. */
    public static final class SkyObject {
        public Sprite sprite;
        public double azimuth;
        public double altitude;
        public float widthDeg;
        public float heightDeg;
        public float rotation;
        public int color;
        public float alpha;
        /** Rotación en pantalla relativa a la dirección hacia el ancla (para grietas y haces). */
        public boolean screenRotation;
    }

    /** Segmento en coordenadas de mundo (estelas de meteoritos, ondas). */
    public static final class WorldLine {
        public double x0, y0, z0, x1, y1, z1;
        public int color;
        public float alpha;
        public float widthPx;
        /** true: los extremos son direcciones del cielo (x0 = acimut, y0 = altura), no posiciones del mundo. */
        public boolean sky;
    }

    public static final int MAX_SKY = 384;
    public static final int MAX_LINES = 192;

    public int skyTint;
    public float skyTintA;
    public int horizon;
    public float horizonA;
    public float blackout;
    public int light;
    public float lightA;
    public float night;
    public int day;
    public float dayA;
    public int whiteout;
    public float whiteoutA;
    public int flash;
    public float flashA;
    public float glitch;
    public boolean glitchSlices;
    public int vignetteColor;
    public float vignette;
    public int tintColor;
    public float tintA;
    public float bloom;
    public float lensDirt;
    public float shake;
    public float shakeFreq;
    /** 0 = ambos ejes, 1 = vertical, 2 = horizontal. */
    public int shakeAxis;
    public float duck = 1;
    public float exposure = 1;

    public final SkyObject[] sky = new SkyObject[MAX_SKY];
    public int skyCount;
    public final WorldLine[] lines = new WorldLine[MAX_LINES];
    public int lineCount;

    public FrameState() {
        for (int i = 0; i < sky.length; i++) {
            sky[i] = new SkyObject();
        }
        for (int i = 0; i < lines.length; i++) {
            lines[i] = new WorldLine();
        }
    }

    public void reset() {
        skyTintA = horizonA = blackout = lightA = night = dayA = whiteoutA = flashA = glitch = vignette = tintA = 0;
        bloom = lensDirt = shake = shakeFreq = 0;
        glitchSlices = false;
        shakeAxis = 0;
        duck = 1;
        skyCount = 0;
        lineCount = 0;
    }

    /** Añade un objeto al cielo; null si se ha llegado al tope (el objeto se descarta). */
    public SkyObject sky(Sprite sprite, double azimuth, double altitude, float sizeDeg, int color, float alpha) {
        if (skyCount >= sky.length || alpha <= 0.003f) {
            return null;
        }
        SkyObject o = sky[skyCount++];
        o.sprite = sprite;
        o.azimuth = azimuth;
        o.altitude = altitude;
        o.widthDeg = sizeDeg;
        o.heightDeg = sizeDeg;
        o.rotation = 0;
        o.color = color;
        o.alpha = Math.min(1, alpha);
        o.screenRotation = false;
        return o;
    }

    public WorldLine line(double x0, double y0, double z0, double x1, double y1, double z1, int color, float alpha, float widthPx) {
        if (lineCount >= lines.length || alpha <= 0.003f) {
            return null;
        }
        WorldLine l = lines[lineCount++];
        l.x0 = x0;
        l.y0 = y0;
        l.z0 = z0;
        l.x1 = x1;
        l.y1 = y1;
        l.z1 = z1;
        l.color = color;
        l.alpha = Math.min(1, alpha);
        l.widthPx = widthPx;
        l.sky = false;
        return l;
    }

    /** Segmento entre dos direcciones del cielo (grietas, llamaradas, estelas): siempre unido por sus extremos. */
    public WorldLine skyLine(double az0, double alt0, double az1, double alt1, int color, float alpha, float widthPx) {
        WorldLine l = line(az0, alt0, 0, az1, alt1, 0, color, alpha, widthPx);
        if (l != null) {
            l.sky = true;
        }
        return l;
    }

    // ------------------------------------------------------------------------------------------------ mezcla

    /** Mezcla un color en una capa: el más intenso domina y los demás tiñen en proporción. */
    static int blend(int current, float currentA, int color, float alpha) {
        if (currentA <= 0) {
            return color;
        }
        float w = alpha / (alpha + currentA);
        int r = (int) (((current >> 16) & 0xFF) * (1 - w) + ((color >> 16) & 0xFF) * w);
        int g = (int) (((current >> 8) & 0xFF) * (1 - w) + ((color >> 8) & 0xFF) * w);
        int b = (int) ((current & 0xFF) * (1 - w) + (color & 0xFF) * w);
        return (r << 16) | (g << 8) | b;
    }

    public void tintSky(int color, float alpha) {
        skyTint = blend(skyTint, skyTintA, color, alpha);
        skyTintA = Math.max(skyTintA, alpha);
    }

    public void tintHorizon(int color, float alpha) {
        horizon = blend(horizon, horizonA, color, alpha);
        horizonA = Math.max(horizonA, alpha);
    }

    public void tintLight(int color, float alpha) {
        light = blend(light, lightA, color, alpha);
        lightA = Math.max(lightA, alpha);
    }

    public void addDay(int color, float alpha) {
        day = blend(day, dayA, color, alpha);
        dayA = Math.max(dayA, alpha);
    }

    public void addWhiteout(int color, float alpha) {
        whiteout = blend(whiteout, whiteoutA, color, alpha);
        whiteoutA = Math.max(whiteoutA, alpha);
    }

    public void addFlash(int color, float alpha) {
        flash = blend(flash, flashA, color, alpha);
        flashA = Math.max(flashA, alpha);
    }

    public void addVignette(int color, float alpha) {
        vignetteColor = blend(vignetteColor, vignette, color, alpha);
        vignette = Math.max(vignette, alpha);
    }

    public void addTint(int color, float alpha) {
        tintColor = blend(tintColor, tintA, color, alpha);
        tintA = Math.max(tintA, alpha);
    }

    /** El temblor de mayor amplitud gana (no se suman). */
    public void addShake(float amplitude, float frequency, int axis) {
        if (amplitude > shake) {
            shake = amplitude;
            shakeFreq = frequency;
            shakeAxis = axis;
        }
    }
}
