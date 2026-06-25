package com.smartfridge.worker;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import com.smartfridge.service.ImageScanService;
import com.smartfridge.util.ModelHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class ImageScanWorker implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ImageScanWorker.class);

    private final ImageScanService scanService;
    private final ModelHolder modelHolder;

    public ImageScanWorker(ImageScanService scanService, ModelHolder modelHolder) {
        this.scanService = scanService;
        this.modelHolder = modelHolder;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Thread worker = new Thread(() -> {
            try {
                var model = modelHolder.awaitModel();
                log.info("[ScanWorker] Ready. Listening for scan requests...");

                while (!Thread.currentThread().isInterrupted()) {
                    ImageScanService.ScanRequest req = scanService.getQueue().take();

                    try (Predictor<Image, Classifications> predictor = model.newPredictor()) {
                        log.info("[ScanWorker] Processing scan for userId={} | " +
                                        "category='{}' quantity={} unit='{}' expiryDate='{}'",
                                req.getUserId(),
                                req.getDto().getCategory(),
                                req.getDto().getQuantity(),
                                req.getDto().getUnit(),
                                req.getDto().getExpiryDate());

                        Image img = ImageFactory.getInstance()
                                .fromInputStream(new ByteArrayInputStream(req.getImageBytes()));

                        Classifications result = predictor.predict(img);

                        // Top-1 prediction only — that's what gets saved as the item name
                        String label = result.best().getClassName();

                        log.info("[ScanWorker] userId={} → predicted '{}'",
                                req.getUserId(), label);

                        req.getFuture().complete(label);

                    } catch (Exception e) {
                        log.error("[ScanWorker] Inference failed for userId={}",
                                req.getUserId(), e);
                        req.getFuture().completeExceptionally(e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[ScanWorker] Worker thread interrupted");
            }
        }, "scan-worker-thread");

        worker.setDaemon(true);
        worker.start();
    }
}