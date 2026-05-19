package com.dfpp.worker.processor;

import com.dfpp.common.event.FileUploadedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/** Real image analysis: decode dimensions and generate a 256px thumbnail on disk. */
@Component
public class ImageProcessor implements FileProcessor {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(String fileType) {
        return "IMAGE".equalsIgnoreCase(fileType);
    }

    @Override
    public String process(FileUploadedEvent event, IntConsumer progress) throws Exception {
        File src = new File(event.storagePath());
        progress.accept(15);

        BufferedImage img = ImageIO.read(src);
        if (img == null) {
            throw new IllegalStateException("File is not a decodable image");
        }
        progress.accept(50);

        Path thumbPath = Path.of(src.getParent(), "thumb_" + src.getName() + ".png");
        Thumbnails.of(src)
                .size(256, 256)
                .keepAspectRatio(true)
                .outputFormat("png")
                .toFile(thumbPath.toFile());
        progress.accept(90);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "IMAGE");
        result.put("width", img.getWidth());
        result.put("height", img.getHeight());
        result.put("megapixels", Math.round((img.getWidth() * (long) img.getHeight()) / 10_000.0) / 100.0);
        result.put("hasAlpha", img.getColorModel().hasAlpha());
        result.put("thumbnailPath", thumbPath.toString());
        result.put("thumbnailBytes", Files.size(thumbPath));
        progress.accept(100);
        return mapper.writeValueAsString(result);
    }
}
