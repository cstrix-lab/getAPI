package com.example.getttsapi.service;

import com.google.gson.JsonObject;
import com.example.getttsapi.dto.SttResponse;
import com.example.getttsapi.dto.TtsRequest;
import com.example.getttsapi.dto.TtsResponse;
import com.example.getttsapi.model.SpeechRecord;
import com.example.getttsapi.repository.SpeechRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SpeechService {

    @Autowired
    private UzbekVoiceApiClient uzbekVoiceApiClient;

    @Autowired
    private SpeechRepository speechRepository;

    /**
     * Audio faylni matnga o'tkazish (STT)
     */
    public SttResponse convertAudioToText(MultipartFile audioFile, String language,
                                          String model, boolean returnOffsets, boolean blocking) {
        try {
            System.out.println("========== STT REQUEST STARTED ==========");
            System.out.println("📁 File: " + audioFile.getOriginalFilename());
            System.out.println("📏 Size: " + audioFile.getSize() + " bytes");
            System.out.println("🌐 Language: " + language);
            System.out.println("🎵 Model: " + model);

            if (audioFile.isEmpty()) {
                throw new RuntimeException("Audio fayl bo'sh!");
            }

            // Vaqtinchalik fayl saqla
            String tempFilePath = System.getProperty("java.io.tmpdir") + "/" + System.currentTimeMillis() + ".mp3";
            File tempFile = new File(tempFilePath);
            audioFile.transferTo(tempFile);

            System.out.println("💾 Temp file saved: " + tempFilePath);
            System.out.println("📂 Temp file exists: " + tempFile.exists());

            // API ga yuborish
            System.out.println("🔌 Calling Uzbekvoice API...");
            JsonObject apiResponse = uzbekVoiceApiClient.speechToText(
                    tempFilePath, language, model, returnOffsets, blocking);

            System.out.println("📨 API Response: " + apiResponse.toString());

            // Javobni qayta ishlash
            SpeechRecord record = new SpeechRecord();
            record.setType("STT");
            record.setLanguage(language);
            record.setStatus("SUCCESS");
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());

            String extractedText = "";
            if (apiResponse.has("result") && apiResponse.get("result").isJsonObject()) {
                JsonObject result = apiResponse.get("result").getAsJsonObject();
                if (result.has("text")) {
                    extractedText = result.get("text").getAsString();
                    record.setText(extractedText);
                    System.out.println("✅ Text extracted: " + extractedText);
                }
            }

            if (apiResponse.has("id")) {
                record.setExternalId(apiResponse.get("id").getAsString());
            }

            speechRepository.save(record);

            System.out.println("💾 Record saved to DB");
            System.out.println("========== STT REQUEST COMPLETED ==========\n");

            return new SttResponse(
                    record.getExternalId(),
                    extractedText,
                    "SUCCESS"
            );

        } catch (IOException e) {
            System.err.println("❌ FILE ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Fayl qayta ishlash xatosi: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ GENERAL ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Xato: " + e.getMessage());
        }
    }

    /**
     * Matni nutqqa o'tkazish (TTS)
     */
    public TtsResponse convertTextToSpeech(TtsRequest ttsRequest) {
        try {
            System.out.println("========== TTS REQUEST STARTED ==========");
            System.out.println("📝 Text: " + ttsRequest.getText());
            System.out.println("🎵 Model: " + (ttsRequest.getModel() != null ? ttsRequest.getModel() : "lola"));

            String model = ttsRequest.getModel() != null ? ttsRequest.getModel() : "lola";
            boolean blocking = ttsRequest.isBlocking();

            JsonObject apiResponse = uzbekVoiceApiClient.textToSpeechWithModel(
                    ttsRequest.getText(),
                    model,
                    blocking
            );

            System.out.println("📨 Initial API Response: " + apiResponse.toString());

            SpeechRecord record = new SpeechRecord();
            record.setType("TTS");
            record.setText(ttsRequest.getText());
            record.setLanguage("uz");
            record.setStatus("PROCESSING");
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());

            String ttsId = null;
            if (apiResponse.has("id")) {
                ttsId = apiResponse.get("id").getAsString();
                record.setExternalId(ttsId);
                System.out.println("📌 TTS ID: " + ttsId);
            }

            // ✅ Agar status "SUCCESS" bo'lsa URL olish
            String audioUrl = null;
            if (apiResponse.has("result") && apiResponse.get("result").isJsonObject()) {
                JsonObject result = apiResponse.get("result").getAsJsonObject();
                if (result.has("url")) {
                    audioUrl = result.get("url").getAsString();
                    System.out.println("✅ Audio URL received immediately");
                }
            }

            // ❌ Agar status "PROGRESS" bo'lsa polling qilish
            if (audioUrl == null && ttsId != null) {
                System.out.println("⏳ Status PROGRESS, polling...");
                audioUrl = pollForTtsResult(ttsId);
            }

            if (audioUrl != null && !audioUrl.isEmpty()) {
                record.setAudioUrl(audioUrl);
                record.setStatus("SUCCESS");
                speechRepository.save(record);

                System.out.println("✅ Record saved to DB");
                System.out.println("========== TTS REQUEST COMPLETED ==========\n");

                return new TtsResponse(audioUrl, "wav", true);
            } else {
                record.setStatus("FAILED");
                speechRepository.save(record);

                System.err.println("❌ No audio URL obtained");
                System.out.println("========== TTS REQUEST FAILED ==========\n");
                return new TtsResponse(null, "wav", false);
            }

        } catch (Exception e) {
            System.err.println("❌ TTS ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("TTS xatosi: " + e.getMessage());
        }
    }

    /**
     * Polling: TTS status tekshirish (maksimal 30 soniya)
     */
    private String pollForTtsResult(String ttsId) {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(1000);  // 1 soniya kutish
                attempt++;

                System.out.println("🔄 Poll attempt " + attempt + "/" + maxAttempts);

                JsonObject statusResponse = uzbekVoiceApiClient.checkTtsStatus(ttsId);

                if (statusResponse.has("status")) {
                    String status = statusResponse.get("status").getAsString();
                    System.out.println("📊 Status: " + status);

                    if ("SUCCESS".equals(status)) {
                        if (statusResponse.has("result") && statusResponse.get("result").isJsonObject()) {
                            JsonObject result = statusResponse.get("result").getAsJsonObject();
                            if (result.has("url")) {
                                String url = result.get("url").getAsString();
                                System.out.println("✅ Audio URL received after " + attempt + " attempts");
                                return url;
                            }
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("❌ Polling interrupted: " + e.getMessage());
                break;
            }
        }

        System.err.println("❌ Polling timeout after " + maxAttempts + " attempts");
        return null;
    }

    /**
     * Barcha yozuvlarni olish
     */
    public List<SpeechRecord> getAllRecords() {
        return speechRepository.findAll();
    }

    /**
     * ID bo'yicha yozuvni olish
     */
    public SpeechRecord getRecordById(Long id) {
        return speechRepository.findById(id).orElse(null);
    }
}