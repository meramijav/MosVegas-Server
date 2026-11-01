package studio.merami.slimcore.systems;

import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadioLibraryTest {
    private static final Path SOUNDS = TestPacks.BUILT_IN.resolve("assets/slimvehicle/sounds");

    private static byte[] sound(String name) throws IOException {
        return Files.readAllBytes(SOUNDS.resolve(name + RadioLibrary.OGG));
    }

    @Test
    void lengthIsReadFromTheOggPagesLikeTheDecoder() throws IOException {
        assertEquals(8.0, RadioLibrary.length(sound("radio")), 1e-9, "radio.ogg as stb_vorbis_stream_length_in_samples gives it");
        assertEquals(13.150363, RadioLibrary.length(sound("siren")), 1e-6, "siren.ogg");
        assertEquals(.5, RadioLibrary.length(sound("horn")), 1e-9, "horn.ogg");
        byte[] radio = sound("radio");
        double cut = RadioLibrary.length(Arrays.copyOf(radio, radio.length / 2));
        assertTrue(cut > 0 && cut < 8, "A file cut in half ends at its last whole page: " + cut);
        assertEquals(0, RadioLibrary.length("not music at all".getBytes(StandardCharsets.US_ASCII)), "Not Ogg");
        byte[] opus = radio.clone();
        System.arraycopy("OpusHead".getBytes(StandardCharsets.US_ASCII), 0, opus, 28, 8);
        assertEquals(0, RadioLibrary.length(opus), "Ogg without Vorbis, which the game cannot decode");
    }

    @Test
    void subfoldersBecomeStationsAndTracksKeepFileOrder() throws IOException {
        Path folder = Files.createTempDirectory("slimvehicle-radio");
        try {
            Files.write(folder.resolve("Loose_song.ogg"), sound("radio"));
            Files.createDirectories(folder.resolve("Rock FM"));
            Files.write(folder.resolve("Rock FM/02_two.ogg"), sound("bell"));
            Files.write(folder.resolve("Rock FM/01_one.ogg"), sound("horn"));
            Files.createDirectories(folder.resolve("jazz/deep"));
            Files.write(folder.resolve("jazz/deep/inner.ogg"), sound("siren"));
            Files.writeString(folder.resolve("jazz/broken.ogg"), "not music", StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("notes.txt"), "ignored", StandardCharsets.UTF_8);

            RadioLibrary library = new RadioLibrary(folder, List.of("readme"));
            assertTrue(library.refresh(), "The first look publishes what is there");
            assertTrue(Files.isRegularFile(folder.resolve("README.txt")), "The folder explains itself");
            List<RadioLibrary.Station> stations = library.stations();
            assertEquals(List.of(RadioLibrary.OWN, "jazz", "Rock FM"), stations.stream().map(RadioLibrary.Station::name).toList(), "Loose files first, then stations by name ignoring case");
            assertEquals(List.of("Loose song"), titles(stations.get(0)));
            assertEquals(List.of("inner"), titles(stations.get(1)), "Deeper folders belong to the top one; a file that is not Vorbis is left out");
            assertEquals(List.of("01 one", "02 two"), titles(stations.get(2)), "Tracks in file name order");
            RadioLibrary.Track bell = stations.get(2).tracks().get(1);
            assertEquals(RadioLibrary.hash(sound("bell")), bell.hash(), "Tracks are known by the SHA-1 of their bytes");
            assertEquals(40, bell.hash().length());
            assertEquals((long) sound("bell").length, bell.size());
            assertEquals(1.0, bell.seconds(), 1e-9);
            assertEquals(folder.resolve("Rock FM/02_two.ogg"), bell.file());
            assertEquals(.5 + 1.0, stations.get(2).seconds(), 1e-9);
        } finally {
            delete(folder);
        }
    }

    @Test
    void aNewFileWaitsUntilTheFolderHoldsStill() throws IOException {
        Path folder = Files.createTempDirectory("slimvehicle-radio");
        try {
            RadioLibrary library = new RadioLibrary(folder, List.of());
            assertTrue(library.refresh(), "An empty folder is published once");
            assertTrue(library.stations().isEmpty());
            assertFalse(library.refresh(), "Nothing changed");

            Path file = folder.resolve("Upload/song.ogg");
            Files.createDirectories(file.getParent());
            byte[] radio = sound("radio");
            Files.write(file, Arrays.copyOf(radio, radio.length / 3));
            assertFalse(library.refresh(), "A file that just appeared may still be copying");
            Files.write(file, Arrays.copyOfRange(radio, radio.length / 3, radio.length), StandardOpenOption.APPEND);
            assertFalse(library.refresh(), "It grew since the last look");
            assertTrue(library.stations().isEmpty(), "The half file never reached the players");
            assertTrue(library.refresh(), "Unchanged for one interval: published");
            assertEquals(RadioLibrary.hash(radio), library.stations().get(0).tracks().get(0).hash(), "with the whole file");
            assertFalse(library.refresh());

            Files.delete(file);
            assertFalse(library.refresh(), "A removal settles like an addition");
            assertTrue(library.refresh());
            assertTrue(library.stations().isEmpty());
        } finally {
            delete(folder);
        }
    }

    private static List<String> titles(RadioLibrary.Station station) {
        return station.tracks().stream().map(RadioLibrary.Track::title).toList();
    }

    private static void delete(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
