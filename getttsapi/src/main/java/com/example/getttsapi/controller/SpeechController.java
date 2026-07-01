package com.example.getttsapi.controller;


import com.example.getttsapi.dto.SttResponse;
import com.example.getttsapi.dto.TtsRequest;
import com.example.getttsapi.dto.TtsResponse;
import com.example.getttsapi.model.SpeechRecord;
import com.example.getttsapi.service.SpeechService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/speech")
@CrossOrigin(origins = "*")
public class SpeechController {

    @Autowired
    private SpeechService speechService;

    /**
     * Audio faylni matnga o'tkazish
     * POST /api/v1/speech/stt
     */
    @PostMapping("/stt")
    public ResponseEntity<?> speechToText(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", defaultValue = "uz") String language,
            @RequestParam(value = "model", defaultValue = "general") String model,
            @RequestParam(value = "return_offsets", defaultValue = "false") boolean returnOffsets,
            @RequestParam(value = "blocking", defaultValue = "true") boolean blocking) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(new HashMap<String, String>() {{
                    put("error", "Fayl bo'sh");
                }});
            }

            SttResponse response = speechService.convertAudioToText(
                    file, language, model, returnOffsets, blocking);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new HashMap<String, String>() {{
                        put("error", e.getMessage());
                    }});
        }
    }

    /**
     * Matni nutqqa o'tkazish
     * POST /api/v1/speech/tts
     */
    @PostMapping("/tts")
    public ResponseEntity<?> textToSpeech(@RequestBody TtsRequest ttsRequest) {
        try {
            if (ttsRequest.getText() == null || ttsRequest.getText().isEmpty()) {
                return ResponseEntity.badRequest().body(new HashMap<String, String>() {{
                    put("error", "Matn bo'sh");
                }});
            }

            TtsResponse response = speechService.convertTextToSpeech(ttsRequest);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new HashMap<String, String>() {{
                        put("error", e.getMessage());
                    }});
        }
    }

    /**
     * TTS status tekshirish va audio URL olish
     * GET /api/v1/speech/tts/status/{ttsId}
     */
    @GetMapping("/tts/status/{ttsId}")
    public ResponseEntity<?> getTtsStatus(@PathVariable String ttsId) {
        try {
            System.out.println("🔍 Checking TTS status for ID: " + ttsId);
            Map<String, Object> status = speechService.checkTtsStatus(ttsId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new HashMap<String, String>() {{
                        put("error", e.getMessage());
                    }});
        }
    }

    /**
     * Audio faylni URL orqali olish va download qilish
     * GET /api/v1/speech/download/{ttsId}
     */
    @GetMapping("/download/{ttsId}")
    public ResponseEntity<?> downloadAudio(@PathVariable String ttsId) {
        try {
            System.out.println("⬇️ Downloading audio for TTS ID: " + ttsId);
            
            // Avval TTS record'ni ID bo'yicha olish
            Optional<SpeechRecord> recordOpt = speechService.getRecordByExternalId(ttsId);
            
            if (recordOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            SpeechRecord record = recordOpt.get();
            
            // Agar audio URL mavjud bo'lsa
            if (record.getAudioUrl() != null && !record.getAudioUrl().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("id", record.getId());
                response.put("externalId", record.getExternalId());
                response.put("text", record.getText());
                response.put("audioUrl", record.getAudioUrl());
                response.put("status", record.getStatus());
                response.put("createdAt", record.getCreatedAt());
                return ResponseEntity.ok(response);
            } else {
                // Agar hali ready bo'lmasa, status'ni tekshirish
                Map<String, Object> status = speechService.checkTtsStatus(ttsId);
                status.put("id", record.getId());
                status.put("message", "TTS still processing, please check again later");
                return ResponseEntity.accepted().body(status);
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new HashMap<String, String>() {{
                        put("error", e.getMessage());
                    }});
        }
    }

    /**
     * Barcha yozuvlarni olish
     * GET /api/v1/speech/records
     */
    @GetMapping("/records")
    public ResponseEntity<List<SpeechRecord>> getAllRecords() {
        List<SpeechRecord> records = speechService.getAllRecords();
        return ResponseEntity.ok(records);
    }

    /**
     * ID bo'yicha yozuv olish (Database ID)
     * GET /api/v1/speech/records/{id}
     */
    @GetMapping("/records/{id}")
    public ResponseEntity<?> getRecordById(@PathVariable Long id) {
        SpeechRecord record = speechService.getRecordById(id);
        if (record != null) {
            return ResponseEntity.ok(record);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * External ID bo'yicha yozuv olish (Uzbekvoice API ID)
     * GET /api/v1/speech/records/external/{externalId}
     */
    @GetMapping("/records/external/{externalId}")
    public ResponseEntity<?> getRecordByExternalId(@PathVariable String externalId) {
        Optional<SpeechRecord> record = speechService.getRecordByExternalId(externalId);
        if (record.isPresent()) {
            return ResponseEntity.ok(record.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
