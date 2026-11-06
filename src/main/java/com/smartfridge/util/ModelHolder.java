package com.smartfridge.util;

import ai.djl.modality.Classifications;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Component
public class ModelHolder {

    private static final Logger log =
            LoggerFactory.getLogger(ModelHolder.class);

    private volatile ZooModel<Image, Classifications> model;
    private volatile Throwable loadFailure;

    private final CountDownLatch readyLatch = new CountDownLatch(1);

    public static final List<String> CLASS_NAMES = List.of(
            "Caesar Salad",
            "Chicken Wings",
            "French Fries",
            "Fried Rice",
            "Hamburger",
            "Ice Cream",
            "Pizza",
            "Spaghetti Bolognese",
            "Steak",
            "Sushi"
    );

    @PostConstruct
    public void loadInBackground() {
        Thread loaderThread = new Thread(() -> {
            try {
                /*
                 * Local development default:
                 * src/main/resources/model_traced.pt
                 *
                 * Docker overrides MODEL_PATH with:
                 * /app/models/model_traced.pt
                 */
                String configuredPath = System.getenv().getOrDefault(
                        "MODEL_PATH",
                        "src/main/resources/model_traced.pt"
                );

                Path modelPath = Paths.get(configuredPath)
                        .toAbsolutePath()
                        .normalize();

                if (!Files.isRegularFile(modelPath)) {
                    throw new IllegalStateException(
                            "Model file not found: " + modelPath
                    );
                }

                log.info(
                        "[ModelHolder] Loading model from {}",
                        modelPath
                );

                ImageClassificationTranslator translator =
                        ImageClassificationTranslator.builder()
                                .addTransform(new Resize(224, 224))
                                .addTransform(new ToTensor())
                                .addTransform(new Normalize(
                                        new float[]{
                                                0.485f,
                                                0.456f,
                                                0.406f
                                        },
                                        new float[]{
                                                0.229f,
                                                0.224f,
                                                0.225f
                                        }
                                ))
                                .optSynset(CLASS_NAMES)
                                .build();

                Criteria<Image, Classifications> criteria =
                        Criteria.builder()
                                .setTypes(
                                        Image.class,
                                        Classifications.class
                                )
                                .optModelPath(modelPath)
                                .optEngine("PyTorch")
                                .optOption("mapLocation", "cpu")
                                .optTranslator(translator)
                                .optProgress(new ProgressBar())
                                .build();

                model = criteria.loadModel();

                log.info("[ModelHolder] Model ready");

            } catch (Throwable e) {
                loadFailure = e;
                log.error(
                        "[ModelHolder] Failed to load model",
                        e
                );

            } finally {
                /*
                 * Release waiting requests even when loading fails.
                 */
                readyLatch.countDown();
            }
        }, "model-loader-thread");

        loaderThread.setDaemon(true);
        loaderThread.start();
    }

    public ZooModel<Image, Classifications> awaitModel()
            throws InterruptedException {

        readyLatch.await();

        if (loadFailure != null) {
            throw new IllegalStateException(
                    "The image-classification model failed to load",
                    loadFailure
            );
        }

        if (model == null) {
            throw new IllegalStateException(
                    "Model loading completed without producing a model"
            );
        }

        return model;
    }

    public boolean isReady() {
        return readyLatch.getCount() == 0
                && model != null
                && loadFailure == null;
    }

    public boolean hasFailed() {
        return readyLatch.getCount() == 0
                && loadFailure != null;
    }
}