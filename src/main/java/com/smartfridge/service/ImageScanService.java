package com.smartfridge.service;

import com.smartfridge.dto.FridgeItemDTO;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class ImageScanService {

    private final BlockingQueue<ScanRequest> queue = new LinkedBlockingQueue<>(50);

    public CompletableFuture<String> submitScan(byte[] imageBytes, String originalFilename,
                                                int userId, FridgeItemDTO dto) {
        ScanRequest req = new ScanRequest(imageBytes, originalFilename, userId, dto);
        if (!queue.offer(req)) {
            req.getFuture().completeExceptionally(
                    new IllegalStateException("Scan queue is full. Please try again."));
        }
        return req.getFuture();
    }

    public BlockingQueue<ScanRequest> getQueue() {
        return queue;
    }

    public static class ScanRequest {
        private final byte[] imageBytes;
        private final String originalFilename;
        private final int userId;
        private final FridgeItemDTO dto;
        private final CompletableFuture<String> future = new CompletableFuture<>();

        public ScanRequest(byte[] imageBytes, String originalFilename, int userId, FridgeItemDTO dto) {
            this.imageBytes = imageBytes;
            this.originalFilename = originalFilename;
            this.userId = userId;
            this.dto = dto;
        }

        public byte[] getImageBytes()       { return imageBytes; }
        public String getOriginalFilename() { return originalFilename; }
        public int getUserId()              { return userId; }
        public FridgeItemDTO getDto()       { return dto; }
        public CompletableFuture<String> getFuture() { return future; }
    }
}