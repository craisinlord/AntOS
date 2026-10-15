package com.craisinlord.antos.content.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ComputerDesktopState {
    public static final Identifier DEFAULT_WALLPAPER = Identifier.fromNamespaceAndPath("antos", "grid_ant");
    public static final int MAX_UNLOCKED_WALLPAPERS = 64;
    public static final int MAX_INSTALLED_PROGRAMS = 64;
    public static final int MAX_ICON_POSITIONS = 64;
    public static final int MAX_ID_LENGTH = 128;
    public static final int MIN_ICON_COORDINATE = 0;
    public static final int MAX_ICON_COORDINATE = 4096;
    private static final String SELECTED_WALLPAPER_TAG = "SelectedWallpaper";
    private static final String UNLOCKED_WALLPAPERS_TAG = "UnlockedWallpapers";
    private static final String INSTALLED_PROGRAMS_TAG = "InstalledPrograms";
    private static final String ICON_POSITIONS_TAG = "IconPositions";
    private static final String ID_TAG = "Id";
    private static final String X_TAG = "X";
    private static final String Y_TAG = "Y";
    private Identifier selectedWallpaper;
    private final Set<Identifier> unlockedWallpaperIds = new LinkedHashSet<>();
    private final Set<Identifier> installedProgramIds = new LinkedHashSet<>();
    private final Map<Identifier, IconPosition> iconPositions = new LinkedHashMap<>();

    public Identifier selectedWallpaper() {
        return selectedWallpaper;
    }

    public Set<Identifier> unlockedWallpaperIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(unlockedWallpaperIds));
    }

    public Set<Identifier> installedProgramIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(installedProgramIds));
    }

    public Map<Identifier, IconPosition> iconPositions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(iconPositions));
    }

    public boolean hasWallpaper(Identifier id) {
        return id != null && unlockedWallpaperIds.contains(id);
    }

    public boolean hasProgram(Identifier id) {
        return id != null && installedProgramIds.contains(id);
    }

    public IconPosition iconPosition(Identifier id) {
        return iconPositions.get(id);
    }

    public boolean unlockWallpaper(Identifier id) {
        if (!validId(id) || unlockedWallpaperIds.contains(id)) {
            return false;
        }
        if (unlockedWallpaperIds.size() >= MAX_UNLOCKED_WALLPAPERS) {
            return false;
        }
        return unlockedWallpaperIds.add(id);
    }

    public boolean installProgram(Identifier id) {
        if (!validId(id) || installedProgramIds.size() >= MAX_INSTALLED_PROGRAMS && !installedProgramIds.contains(id)) {
            return false;
        }
        return installedProgramIds.add(id);
    }

    public boolean uninstallProgram(Identifier id) {
        if (id == null || !installedProgramIds.remove(id)) {
            return false;
        }
        iconPositions.remove(id);
        return true;
    }

    public boolean selectWallpaper(Identifier id) {
        if (id != null && !unlockedWallpaperIds.contains(id)) {
            return false;
        }
        if (id == null ? selectedWallpaper == null : id.equals(selectedWallpaper)) {
            return false;
        }
        selectedWallpaper = id;
        return true;
    }

    public boolean setIconPosition(Identifier id, int x, int y) {
        if (!validId(id) || x < MIN_ICON_COORDINATE || x > MAX_ICON_COORDINATE || y < MIN_ICON_COORDINATE || y > MAX_ICON_COORDINATE) {
            return false;
        }
        IconPosition position = new IconPosition(x, y);
        if (position.equals(iconPositions.get(id))) {
            return false;
        }
        if (!iconPositions.containsKey(id) && iconPositions.size() >= MAX_ICON_POSITIONS) {
            return false;
        }
        iconPositions.put(id, position);
        return true;
    }

    public boolean removeIconPosition(Identifier id) {
        return id != null && iconPositions.remove(id) != null;
    }

    public void clear() {
        unlockedWallpaperIds.clear();
        installedProgramIds.clear();
        iconPositions.clear();
        unlockedWallpaperIds.add(DEFAULT_WALLPAPER);
        selectedWallpaper = DEFAULT_WALLPAPER;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (selectedWallpaper != null) {
            tag.putString(SELECTED_WALLPAPER_TAG, selectedWallpaper.toString());
        }
        tag.put(UNLOCKED_WALLPAPERS_TAG, saveIds(unlockedWallpaperIds));
        tag.put(INSTALLED_PROGRAMS_TAG, saveIds(installedProgramIds));
        ListTag positions = new ListTag();
        for (Map.Entry<Identifier, IconPosition> entry : iconPositions.entrySet()) {
            CompoundTag position = new CompoundTag();
            position.putString(ID_TAG, entry.getKey().toString());
            position.putInt(X_TAG, entry.getValue().x());
            position.putInt(Y_TAG, entry.getValue().y());
            positions.add(position);
        }
        tag.put(ICON_POSITIONS_TAG, positions);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        loadIds(tag.getListOrEmpty(UNLOCKED_WALLPAPERS_TAG), unlockedWallpaperIds, MAX_UNLOCKED_WALLPAPERS);
        Identifier storedSelection = parseId(tag.getStringOr(SELECTED_WALLPAPER_TAG, ""));
        selectedWallpaper = storedSelection != null && unlockedWallpaperIds.contains(storedSelection) ? storedSelection : DEFAULT_WALLPAPER;
        loadIds(tag.getListOrEmpty(INSTALLED_PROGRAMS_TAG), installedProgramIds, MAX_INSTALLED_PROGRAMS);
        ListTag positions = tag.getListOrEmpty(ICON_POSITIONS_TAG);
        for (int index = 0; index < positions.size() && iconPositions.size() < MAX_ICON_POSITIONS; index++) {
            CompoundTag stored = positions.getCompoundOrEmpty(index);
            Identifier id = parseId(stored.getStringOr(ID_TAG, ""));
            int x = stored.getIntOr(X_TAG, 0);
            int y = stored.getIntOr(Y_TAG, 0);
            if (id != null && x >= MIN_ICON_COORDINATE && x <= MAX_ICON_COORDINATE && y >= MIN_ICON_COORDINATE && y <= MAX_ICON_COORDINATE) {
                iconPositions.putIfAbsent(id, new IconPosition(x, y));
            }
        }
    }

    private static ListTag saveIds(Collection<Identifier> ids) {
        ListTag list = new ListTag();
        for (Identifier id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static void loadIds(ListTag list, Set<Identifier> destination, int limit) {
        for (int index = 0; index < list.size() && destination.size() < limit; index++) {
            Identifier id = parseId(list.getStringOr(index, ""));
            if (id != null) {
                destination.add(id);
            }
        }
    }

    private static Identifier parseId(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_ID_LENGTH) {
            return null;
        }
        try {
            return Identifier.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean validId(Identifier id) {
        return id != null && id.toString().length() <= MAX_ID_LENGTH;
    }

    public record IconPosition(int x, int y) {
    }
}


