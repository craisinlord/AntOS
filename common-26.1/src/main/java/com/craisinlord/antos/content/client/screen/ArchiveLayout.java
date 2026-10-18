package com.craisinlord.antos.content.client.screen;

import java.util.ArrayList;
import java.util.List;

record ArchiveLayout(int width, int height, int columns, int contentHeight, List<Cell> cells, List<Section> sections) {
    static final int BODY_TOP = 42;
    static final int FOOTER = 14;
    static final int ROW = 32;
    static final int TILE = 72;
    static final int HEADING = 14;

    static ArchiveLayout create(int width, int height, boolean gallery, int folders, int entries) {
        width = Math.max(100, width);
        height = Math.max(BODY_TOP + FOOTER + 1, height);
        int columns = gallery ? Math.max(1, (width - 8) / 100) : 1;
        int cellHeight = gallery ? TILE : ROW;
        int cellWidth = (width - 8) / columns;
        List<Cell> cells = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        int top = 0;
        int offset = 0;
        for (int group = 0; group < 2; group++) {
            int count = Math.max(0, group == 0 ? folders : entries);
            if (count == 0) continue;
            sections.add(new Section(group == 0, top));
            top += HEADING;
            for (int index = 0; index < count; index++) {
                cells.add(new Cell(offset + index, index % columns * cellWidth, top + index / columns * cellHeight,
                        cellWidth, cellHeight));
            }
            offset += count;
            top += (count + columns - 1) / columns * cellHeight;
        }
        return new ArchiveLayout(width, height, columns, top, List.copyOf(cells), List.copyOf(sections));
    }

    int viewportHeight() {
        return height - BODY_TOP - FOOTER;
    }

    int maximumScroll() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    int reveal(int index, int scroll) {
        if (index < 0 || index >= cells.size()) return Math.max(0, Math.min(maximumScroll(), scroll));
        Cell cell = cells.get(index);
        if (cell.y() < scroll) scroll = cell.y();
        if (cell.y() + cell.height() > scroll + viewportHeight()) scroll = cell.y() + cell.height() - viewportHeight();
        return Math.max(0, Math.min(maximumScroll(), scroll));
    }

    int move(int index, int dx, int dy) {
        if (cells.isEmpty()) return -1;
        if (index < 0 || index >= cells.size()) return 0;
        Cell current = cells.get(index);
        Cell best = null;
        long score = Long.MAX_VALUE;
        for (Cell candidate : cells) {
            int x = candidate.x() - current.x();
            int y = candidate.y() - current.y();
            if (dy != 0 ? y * dy <= 0 : y != 0 || x * dx <= 0) continue;
            long distance = dy != 0 ? Math.abs(y) * 10000L + Math.abs(x) : Math.abs(x);
            if (distance < score) {
                best = candidate;
                score = distance;
            }
        }
        return best == null ? index : best.index();
    }

    record Cell(int index, int x, int y, int width, int height) { }
    record Section(boolean folders, int y) { }
}
