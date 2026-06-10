package com.parkomfy.controller;

import com.parkomfy.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", maxAge = 3600)
public class LegalRestController {

    @GetMapping("/legal/kvkk")
    public ResponseEntity<ApiResponse<Map<String, String>>> getKvkkText() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", "KVKK Aydınlatma Metni");
        body.put("version", "1.0");
        body.put("text",
            "Parkomfy uygulaması kapsamında; ad-soyad, e-posta, telefon numarası, araç plakası ve "
                + "otopark oturum bilgileriniz yalnızca otopark yönetimi, rezervasyon, plaka tanıma "
                + "ve ödeme süreçlerinin yürütülmesi amacıyla işlenmektedir.\n\n"
                + "Kişisel verileriniz, hizmetin sağlanması için gerekli süre boyunca saklanır; "
                + "yasal yükümlülükler sona erdiğinde veya talebiniz üzerine silinir veya anonimleştirilir.\n\n"
                + "Verileriniz üçüncü taraflarla yalnızca yasal zorunluluk veya açık rızanız halinde paylaşılır. "
                + "KVKK kapsamındaki haklarınız (bilgi talebi, düzeltme, silme, itiraz) için "
                + "destek@parkomfy.local adresine başvurabilirsiniz.\n\n"
                + "Kayıt işlemiyle bu metni okuduğunuzu ve kişisel verilerinizin belirtilen amaçlarla "
                + "işlenmesine rıza gösterdiğinizi kabul etmiş olursunuz.");
        return ResponseEntity.ok(ApiResponse.success(body, "KVKK aydınlatma metni"));
    }
}
