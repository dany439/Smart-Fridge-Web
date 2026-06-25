package com.smartfridge.util;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Component
public class ModelHolder {

    private static final Logger log = LoggerFactory.getLogger(ModelHolder.class);

    private volatile ZooModel<Image, ai.djl.modality.Classifications> model;
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    public static final List<String> CLASS_NAMES = List.of(
            "Caesar Salad", "Chicken Wings", "French Fries", "Fried Rice",
            "Hamburger", "Ice Cream", "Pizza", "Spaghetti Bolognese", "Steak", "Sushi"
    );

    @PostConstruct
    public void loadInBackground() {
        Thread t = new Thread(() -> {
            try {
                log.info("[ModelHolder] Loading EfficientNet weights...");

                ImageClassificationTranslator translator = ImageClassificationTranslator
                        .builder()
                        .addTransform(new Resize(224, 224))
                        .addTransform(new ToTensor())
                        .addTransform(new Normalize(
                                new float[]{0.485f, 0.456f, 0.406f},
                                new float[]{0.229f, 0.224f, 0.225f}
                        ))
                        .optSynset(CLASS_NAMES)
                        .build();

                Criteria<Image, ai.djl.modality.Classifications> criteria = Criteria.builder()
                        .setTypes(Image.class, ai.djl.modality.Classifications.class)
                        .optModelPath(Paths.get(getClass().getClassLoader()
                                .getResource("model_traced.pt").toURI()))
                        .optOption("mapLocation", "cpu")
                        .optTranslator(translator)
                        .optProgress(new ProgressBar())
                        .build();

                model = criteria.loadModel();
                readyLatch.countDown();
                log.info("[ModelHolder] Model ready ✓");

            } catch (Exception e) {  // already catches it since Exception is broad enough
                log.error("[ModelHolder] Failed to load model", e);
            }
        }, "model-loader-thread");
        t.setDaemon(true);
        t.start();
    }

    public ZooModel<Image, ai.djl.modality.Classifications> awaitModel()
            throws InterruptedException {
        readyLatch.await();
        return model;
    }

    public boolean isReady() { return readyLatch.getCount() == 0; }
}