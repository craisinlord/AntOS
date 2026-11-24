package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringValidation;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class StudioDialogs {
    private StudioDialogs() { }

    static final class Confirm extends StudioPopup {
        private final AntStudioScreen screen;
        private final String message;
        private final String yes;
        private final Runnable action;

        Confirm(AntStudioScreen screen, String message, String yes, Runnable action) {
            this.screen = screen;
            this.message = message;
            this.yes = yes;
            this.action = action;
        }

        @Override
        boolean shade() { return true; }

        @Override
        void layout(StudioKit kit) { center(kit, 250, 44 + kit.wrapHeight(Component.literal(message), 232, 11)); }

        @Override
        void render(StudioKit kit) {
            kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
            kit.wrap(message, x + 9, y + 10, w - 18, StudioTheme.text);
            int size = kit.buttonWidth(yes);
            kit.button(x + w - size - 8, y + h - 22, size, 16, yes, StudioKit.PRIMARY, true, "", this::accept);
            int cancel = kit.buttonWidth(StudioText.t("cancel"));
            kit.button(x + w - size - cancel - 12, y + h - 22, cancel, 16, StudioText.t("cancel"), StudioKit.NORMAL, true, "", () -> screen.closePopup(this));
        }

        void accept() {
            screen.closePopup(this);
            action.run();
        }

        @Override
        boolean key(int key, int modifiers) {
            if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return false;
            accept();
            return true;
        }
    }

    static final class Raw extends StudioPopup {
        private final AntStudioScreen screen;
        private final StudioKey key;
        private String text;
        private String problem = "";

        Raw(AntStudioScreen screen, StudioKey key) {
            this.screen = screen;
            this.key = key;
            text = StudioJson.PRETTY.toJson(screen.store.view(key));
        }

        @Override
        boolean shade() { return true; }

        @Override
        void layout(StudioKit kit) { center(kit, 460, kit.height - 40); }

        void set(String value) {
            text = value;
            problem = "";
            try { AuthoringValidation.parse(text); }
            catch (RuntimeException exception) { problem = com.craisinlord.antos.content.editor.AuthoringChecks.clean(exception); }
        }

        @Override
        void render(StudioKit kit) {
            kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
            kit.text(StudioText.t("json.title", key.id()), x + 9, y + 8, w - 18, StudioTheme.strong);
            kit.area("json", x + 8, y + 22, w - 16, h - 50, text, AuthoringValidation.MAX_DOCUMENT_CHARS, "{}", !problem.isEmpty(), this::set);
            if (!problem.isEmpty()) kit.text(problem, x + 9, y + h - 17, w - 190, StudioTheme.error);
            int size = kit.buttonWidth(StudioText.t("json.use"));
            kit.button(x + w - size - 8, y + h - 23, size, 16, StudioText.t("json.use"), StudioKit.PRIMARY, problem.isEmpty(), "", this::accept);
            int cancel = kit.buttonWidth(StudioText.t("cancel"));
            kit.button(x + w - size - cancel - 12, y + h - 23, cancel, 16, StudioText.t("cancel"), StudioKit.NORMAL, true, "", () -> screen.closePopup(this));
            int copy = kit.buttonWidth(StudioText.t("copy"));
            kit.button(x + w - size - cancel - copy - 16, y + h - 23, copy, 16, StudioText.t("copy"), StudioKit.GHOST, true, "", () -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
        }

        void accept() {
            try {
                JsonObject body = AuthoringValidation.parse(text);
                screen.closePopup(this);
                screen.store.edit(key, "", target -> {
                    for (String name : java.util.List.copyOf(target.keySet())) target.remove(name);
                    body.entrySet().forEach(entry -> target.add(entry.getKey(), entry.getValue()));
                });
            } catch (RuntimeException exception) { problem = com.craisinlord.antos.content.editor.AuthoringChecks.clean(exception); }
        }
    }

    static final class Prompt extends StudioPopup {
        private final AntStudioScreen screen;
        private final String title;
        private final String note;
        private final Consumer<String> action;
        private String value;

        Prompt(AntStudioScreen screen, String title, String note, String value, Consumer<String> action) {
            this.screen = screen;
            this.title = title;
            this.note = note;
            this.value = value;
            this.action = action;
        }

        String field() { return "prompt"; }

        @Override
        boolean shade() { return true; }

        @Override
        void layout(StudioKit kit) { center(kit, 260, 72 + kit.wrapHeight(Component.literal(note), 242, 11)); }

        @Override
        void render(StudioKit kit) {
            kit.box(x, y, w, h, StudioTheme.window, StudioTheme.accent);
            kit.text(title, x + 9, y + 9, w - 18, StudioTheme.strong);
            int end = kit.wrap(note, x + 9, y + 23, w - 18, StudioTheme.soft);
            kit.field(field(), x + 9, end + 2, w - 18, value, new StudioKit.Field().max(240).enter(this::accept), text -> value = text);
            int size = kit.buttonWidth(StudioText.t("set"));
            kit.button(x + w - size - 8, y + h - 22, size, 16, StudioText.t("set"), StudioKit.PRIMARY, !value.isBlank(), "", this::accept);
            int cancel = kit.buttonWidth(StudioText.t("cancel"));
            kit.button(x + w - size - cancel - 12, y + h - 22, cancel, 16, StudioText.t("cancel"), StudioKit.NORMAL, true, "", () -> screen.closePopup(this));
        }

        void accept() {
            screen.closePopup(this);
            action.accept(value.trim());
        }
    }
}
