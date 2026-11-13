package com.shiroha.mmdskin.render.backend.submit;

import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * 文件职责：在一次实体提交期间向渲染后端传递当前的 {@link SubmitNodeCollector}。
 *
 * <p>Minecraft 26.3 no longer draws entities immediately: renderers hand geometry to a
 * {@link SubmitNodeCollector}. The model render entry points keep their existing signatures, so the
 * platform hooks publish the collector here for the duration of one submit call. Everything runs on
 * the render thread, so plain static state is sufficient.
 */
public final class RenderSubmitContext {
    private static SubmitNodeCollector collector;
    private static int guiPassDepth;

    private RenderSubmitContext() {
    }

    /** Publishes {@code next} and returns the collector that was active before, for {@link #restore}. */
    public static SubmitNodeCollector begin(SubmitNodeCollector next) {
        SubmitNodeCollector previous = collector;
        collector = next;
        return previous;
    }

    public static void restore(SubmitNodeCollector previous) {
        collector = previous;
    }

    /** The collector of the submit call in progress, or {@code null} outside of one. */
    public static SubmitNodeCollector current() {
        return collector;
    }

    /** Marks the start of a GUI entity preview (inventory screen and similar picture-in-picture draws). */
    public static void enterGuiPass() {
        guiPassDepth++;
    }

    public static void exitGuiPass() {
        if (guiPassDepth > 0) {
            guiPassDepth--;
        }
    }

    public static boolean isGuiPass() {
        return guiPassDepth > 0;
    }
}
