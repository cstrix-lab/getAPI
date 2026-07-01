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
     * Barcha yozuvlarni olish
     * GET /api/v1/speech/records
     */
    @GetMapping("/records")
    public ResponseEntity<List<SpeechRecord>> getAllRecords() {
        List<SpeechRecord> records = speechService.getAllRecords();
        return ResponseEntity.ok(records);
    }

    /**
     * ID bo'yicha yozuv olish
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
}