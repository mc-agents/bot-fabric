package kr.junhyung.mcagents.botfabric.tools;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.file.Files;
import java.nio.file.Path;

/** A captured frame at the size a caller asked for, encoded as the PNG that goes back as a blob. */
final class Frames {

    record Frame(int width, int height, byte[] png) {
    }

    private Frames() {
    }

    static Frame of(NativeImage source, int width, int height) throws Exception {
        if (source.getWidth() == width && source.getHeight() == height) {
            return new Frame(width, height, encode(source));
        }
        try (NativeImage scaled = new NativeImage(width, height, false)) {
            source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), scaled);
            return new Frame(width, height, encode(scaled));
        }
    }

    private static byte[] encode(NativeImage image) throws Exception {
        Path file = Files.createTempFile("botfabric-screenshot", ".png");
        try {
            image.writeToFile(file);
            return Files.readAllBytes(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
