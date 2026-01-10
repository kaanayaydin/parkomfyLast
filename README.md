# PARKOMFY - Smart Parking Management System

## Proje Açıklaması

PARKOMFY, mevcut güvenlik kameralarını kullanarak YOLO algoritması ile gerçek zamanlı park yeri doluluk tespiti yapan ve plaka tanıma özelliği sunan akıllı park yönetim sistemidir.

## OOP Yapısı

Bu proje, Object-Oriented Programming (OOP) prensiplerine göre tasarlanmıştır:

### 1. Encapsulation (Kapsülleme)
- Tüm sınıflarda private field'lar kullanılmıştır
- Getter ve setter metodları ile kontrollü erişim sağlanmıştır
- İç detaylar dışarıdan gizlenmiştir

### 2. Inheritance (Kalıtım)
- Interface'ler kullanılarak polimorfizm sağlanmıştır
- `IParkingService`, `IPaymentService`, `IDetectionService` interface'leri
- Service sınıfları bu interface'leri implement eder

### 3. Polymorphism (Çok Biçimlilik)
- Interface'ler sayesinde farklı implementasyonlar kullanılabilir
- Service sınıfları interface'ler üzerinden kullanılır

### 4. Abstraction (Soyutlama)
- Interface'ler ile soyut katmanlar oluşturulmuştur
- Repository pattern ile veri erişimi soyutlanmıştır

## Proje Yapısı

```
src/main/java/com/parkomfy/
├── model/              # Domain model sınıfları
│   ├── LicensePlate.java
│   ├── Vehicle.java
│   ├── ParkingSlot.java
│   ├── ParkingArea.java
│   ├── User.java
│   ├── ParkingSession.java
│   ├── Payment.java
│   ├── PaymentMethod.java
│   ├── Camera.java
│   ├── PricingPolicy.java
│   └── DetectionResult.java
├── service/            # Business logic servisleri
│   ├── IParkingService.java
│   ├── ParkingService.java
│   ├── IPaymentService.java
│   ├── PaymentService.java
│   ├── IDetectionService.java
│   └── DetectionService.java
├── repository/         # Data access layer
│   ├── IParkingRepository.java
│   └── DatabaseManager.java
└── ParkomfyApplication.java  # Main application
```

## Ana Sınıflar

### Model Sınıfları

1. **LicensePlate**: Plaka bilgilerini tutar
2. **Vehicle**: Araç bilgileri, giriş/çıkış zamanları
3. **ParkingSlot**: Park yeri, durumu ve konumu
4. **ParkingArea**: Park alanı, slotlar ve kameralar
5. **User**: Kullanıcı bilgileri ve kayıtlı araçlar
6. **ParkingSession**: Park oturumu, araç-slot ilişkisi
7. **Payment**: Ödeme işlemleri
8. **PaymentMethod**: Ödeme yöntemi (Stripe entegrasyonu)
9. **Camera**: Kamera bilgileri ve izlenen slotlar
10. **PricingPolicy**: Fiyatlandırma politikası
11. **DetectionResult**: YOLO tespit sonuçları

### Service Sınıfları

1. **ParkingService**: Park yönetimi işlemleri
2. **PaymentService**: Ödeme işlemleri (Stripe entegrasyonu)
3. **DetectionService**: YOLO tespit işlemleri

### Repository Sınıfları

1. **DatabaseManager**: MySQL veritabanı işlemleri
2. **IParkingRepository**: Repository interface'i

## Özellikler

- ✅ Gerçek zamanlı park yeri doluluk tespiti
- ✅ Plaka tanıma (LPR)
- ✅ Otomatik fiyatlandırma
- ✅ Stripe ödeme entegrasyonu
- ✅ Kullanıcı yönetimi
- ✅ Park oturumu takibi
- ✅ Veritabanı entegrasyonu

## Kullanım

```bash
# Projeyi derle
javac -d out src/main/java/com/parkomfy/**/*.java

# Çalıştır
java -cp out com.parkomfy.ParkomfyApplication
```

## Gereksinimler

- Java 11 veya üzeri
- MySQL (veritabanı için)
- MySQL JDBC Driver (mysql-connector-java)

## Notlar

- Bu implementasyon OOP yapısını gösterir
- Gerçek YOLO ve EasyOCR entegrasyonları Python servisleri veya JNI ile yapılabilir
- Stripe entegrasyonu gerçek API çağrıları ile tamamlanmalıdır
- Veritabanı şeması ve tablolar oluşturulmalıdır

## Lisans

Bu proje Özyeğin Üniversitesi CS401-402 Senior Project kapsamında geliştirilmiştir.
