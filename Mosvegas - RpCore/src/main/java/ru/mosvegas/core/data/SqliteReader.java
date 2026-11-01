package ru.mosvegas.core.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Read-only reader for the SQLite base of Mosvegas Core 0.0.4 and earlier, so a world moves to JSON without
 * the 5 MB native driver. Follows https://sqlite.org/fileformat2.html: table B-trees, overflow pages and the
 * committed frames of the WAL, which 0.0.4 never checkpointed because it did not close its connection.
 * Rowid tables in UTF-8 only: that is all the old schema had.
 */
final class SqliteReader {
    private static final int[] INTEGER_SIZES = {0, 1, 2, 3, 4, 6, 8};
    private final byte[] main;
    private final Map<Integer, byte[]> wal = new HashMap<>();
    private final int pageSize, usable;
    private final Map<String, Integer> roots = new HashMap<>();
    private final Map<String, List<String>> columns = new HashMap<>();

    SqliteReader(Path database) throws IOException {
        main = Files.readAllBytes(database);
        Path walFile = database.resolveSibling(database.getFileName() + "-wal");
        int walPageSize = Files.exists(walFile) ? readWal(Files.readAllBytes(walFile)) : 0;
        if (main.length < 100 && wal.isEmpty()) { pageSize = usable = 0; return; }
        pageSize = walPageSize != 0 ? walPageSize : pageSize(main);
        byte[] first = page(1);
        if (!new String(first, 0, 16, StandardCharsets.ISO_8859_1).equals("SQLite format 3\0")) throw new IOException("Не SQLite-файл: " + database);
        int encoding = int32(first, 56);
        if (encoding > 1) throw new IOException("Кодировка базы не UTF-8: " + database);
        usable = pageSize - (first[20] & 0xff);
        for (List<Object> row : scan(1)) {
            if (!"table".equals(row.get(0))) continue;
            roots.put((String) row.get(1), ((Long) row.get(3)).intValue());
            columns.put((String) row.get(1), columns((String) row.get(4)));
        }
    }

    /** Rows in rowid order, by column name. A missing table has no rows; a column added later is null in older rows. */
    List<Map<String, Object>> rows(String table) throws IOException {
        if (!roots.containsKey(table)) return List.of();
        List<String> names = columns.get(table);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (List<Object> values : scan(roots.get(table))) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < names.size(); i++) row.put(names.get(i), i < values.size() ? values.get(i) : null);
            rows.add(row);
        }
        return rows;
    }

    private static int pageSize(byte[] header) {
        int size = ((header[16] & 0xff) << 8) | (header[17] & 0xff);
        return size == 1 ? 65536 : size;
    }

    /** Keeps the frames up to the last valid commit; a salt or checksum mismatch ends the log, as in SQLite's own recovery. */
    private int readWal(byte[] log) {
        if (log.length < 32 || (int32(log, 0) & ~1) != 0x377f0682) return 0;
        boolean bigEndian = (int32(log, 0) & 1) == 1;
        int size = int32(log, 8);
        if (size < 512 || size > 65536 || Integer.bitCount(size) != 1) return 0;
        int[] sum = checksum(log, 0, 24, new int[2], bigEndian);
        if (sum[0] != int32(log, 24) || sum[1] != int32(log, 28)) return 0;
        Map<Integer, byte[]> pending = new HashMap<>();
        for (int at = 32; at + 24 + size <= log.length; at += 24 + size) {
            if (int32(log, at + 8) != int32(log, 16) || int32(log, at + 12) != int32(log, 20)) break;
            sum = checksum(log, at + 24, size, checksum(log, at, 8, sum, bigEndian), bigEndian);
            if (sum[0] != int32(log, at + 16) || sum[1] != int32(log, at + 20)) break;
            pending.put(int32(log, at), Arrays.copyOfRange(log, at + 24, at + 24 + size));
            if (int32(log, at + 4) != 0) { wal.putAll(pending); pending.clear(); }
        }
        return wal.isEmpty() ? 0 : size;
    }

    private static int[] checksum(byte[] data, int from, int length, int[] previous, boolean bigEndian) {
        int s0 = previous[0], s1 = previous[1];
        for (int i = from; i < from + length; i += 8) {
            s0 += word(data, i, bigEndian) + s1;
            s1 += word(data, i + 4, bigEndian) + s0;
        }
        return new int[]{s0, s1};
    }

    private static int word(byte[] data, int at, boolean bigEndian) {
        return bigEndian ? int32(data, at) : (data[at] & 0xff) | (data[at + 1] & 0xff) << 8 | (data[at + 2] & 0xff) << 16 | (data[at + 3] & 0xff) << 24;
    }

    private byte[] page(int number) throws IOException {
        byte[] page = wal.get(number);
        if (page != null) return page;
        long offset = (long) (number - 1) * pageSize;
        if (number < 1 || offset + pageSize > main.length) throw new IOException("Нет страницы " + number + ": база повреждена");
        return Arrays.copyOfRange(main, (int) offset, (int) offset + pageSize);
    }

    private List<List<Object>> scan(int root) throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        scan(root, rows, new HashSet<>());
        return rows;
    }

    private void scan(int number, List<List<Object>> rows, Set<Integer> seen) throws IOException {
        if (!seen.add(number)) throw new IOException("Цикл в B-дереве на странице " + number);
        byte[] page = page(number);
        int header = number == 1 ? 100 : 0, type = page[header] & 0xff, cells = int16(page, header + 3);
        if (type == 0x05) {
            for (int i = 0; i < cells; i++) scan(int32(page, int16(page, header + 12 + 2 * i)), rows, seen);
            scan(int32(page, header + 8), rows, seen);
        } else if (type == 0x0d) {
            for (int i = 0; i < cells; i++) rows.add(record(payload(page, int16(page, header + 8 + 2 * i))));
        } else throw new IOException("Страница " + number + " не из таблицы (тип " + type + ")");
    }

    private byte[] payload(byte[] page, int cell) throws IOException {
        Cursor cursor = new Cursor(page, cell);
        long size = cursor.varint();
        cursor.varint(); // rowid
        if (size > Integer.MAX_VALUE - 8) throw new IOException("Запись слишком большая: " + size);
        int total = (int) size, max = usable - 35, local = total;
        if (total > max) {
            int min = (usable - 12) * 32 / 255 - 23, fit = min + (total - min) % (usable - 4);
            local = fit <= max ? fit : min;
        }
        byte[] payload = Arrays.copyOfRange(page, cursor.at, cursor.at + total);
        int filled = local, next = local < total ? int32(page, cursor.at + local) : 0;
        Set<Integer> seen = new HashSet<>();
        while (filled < total) {
            if (next == 0 || !seen.add(next)) throw new IOException("Повреждена цепочка страниц переполнения");
            byte[] overflow = page(next);
            int length = Math.min(total - filled, usable - 4);
            System.arraycopy(overflow, 4, payload, filled, length);
            filled += length;
            next = int32(overflow, 0);
        }
        return payload;
    }

    private static List<Object> record(byte[] record) throws IOException {
        Cursor header = new Cursor(record, 0);
        int end = (int) header.varint(), data = end;
        List<Object> values = new ArrayList<>();
        while (header.at < end) {
            long type = header.varint();
            if (type == 0) values.add(null);
            else if (type <= 6) {
                long value = record[data];
                for (int i = 1; i < INTEGER_SIZES[(int) type]; i++) value = value << 8 | (record[data + i] & 0xff);
                values.add(value);
                data += INTEGER_SIZES[(int) type];
            } else if (type == 7) {
                values.add(Double.longBitsToDouble((long) int32(record, data) << 32 | int32(record, data + 4) & 0xffffffffL));
                data += 8;
            } else if (type == 8 || type == 9) values.add(type - 8);
            else if (type >= 12) {
                int length = (int) ((type - 12) / 2);
                byte[] bytes = Arrays.copyOfRange(record, data, data + length);
                values.add(type % 2 == 0 ? bytes : new String(bytes, StandardCharsets.UTF_8));
                data += length;
            } else throw new IOException("Неизвестный тип значения " + type);
        }
        return values;
    }

    /** Column names from the CREATE TABLE text that SQLite keeps in its schema table; constraints are skipped. */
    static List<String> columns(String sql) {
        List<String> names = new ArrayList<>();
        int open = sql.indexOf('('), close = sql.lastIndexOf(')'), depth = 0, start = open + 1;
        for (int i = start; i < close; i++) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') i = sql.indexOf(c, i + 1);
            else if (c == '[') i = sql.indexOf(']', i + 1);
            else if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) { column(sql.substring(start, i), names); start = i + 1; }
            if (i < 0) throw new IllegalArgumentException("Не разобрать схему: " + sql);
        }
        column(sql.substring(start, close), names);
        return names;
    }

    private static void column(String definition, List<String> names) {
        String text = definition.strip();
        char quote = text.charAt(0);
        String name = switch (quote) {
            case '"', '`' -> text.substring(1, text.indexOf(quote, 1));
            case '[' -> text.substring(1, text.indexOf(']'));
            default -> text.split("[\\s(]", 2)[0];
        };
        if (quote == '"' || quote == '`' || quote == '[' || !Set.of("CONSTRAINT", "PRIMARY", "FOREIGN", "UNIQUE", "CHECK").contains(name.toUpperCase(Locale.ROOT))) names.add(name);
    }

    private static int int16(byte[] data, int at) { return (data[at] & 0xff) << 8 | data[at + 1] & 0xff; }
    private static int int32(byte[] data, int at) { return (data[at] & 0xff) << 24 | (data[at + 1] & 0xff) << 16 | (data[at + 2] & 0xff) << 8 | data[at + 3] & 0xff; }

    private static final class Cursor {
        private final byte[] data;
        private int at;
        Cursor(byte[] data, int at) { this.data = data; this.at = at; }

        long varint() {
            long value = 0;
            for (int i = 0; i < 8; i++) {
                int b = data[at++] & 0xff;
                value = value << 7 | b & 0x7f;
                if (b < 0x80) return value;
            }
            return value << 8 | data[at++] & 0xff;
        }
    }
}
