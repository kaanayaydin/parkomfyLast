# PARKOMFY Uygulamasını Çalıştırma Kılavuzu

## Gereksinimler

- **Java 11 veya üzeri** (Java 17 önerilir)
- Java'nın PATH'e ekli olması

### Java Kurulumunu Kontrol Etme

Terminal/Command Prompt'ta şu komutu çalıştırın:

```bash
java -version
```

Eğer Java yüklü değilse:
- Windows: [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) veya [OpenJDK](https://adoptium.net/)
- Mac: `brew install openjdk@17`
- Linux: `sudo apt-get install openjdk-17-jdk`

---

## Yöntem 1: Script ile Çalıştırma (En Kolay)

### Windows için:

1. `run.bat` dosyasına çift tıklayın
   VEYA
2. Command Prompt'ta:
   ```cmd
   run.bat
   ```

### Mac/Linux için:

1. Terminal'de script'e çalıştırma izni verin:
   ```bash
   chmod +x run.sh
   ```

2. Script'i çalıştırın:
   ```bash
   ./run.sh
   ```

---

## Yöntem 2: Manuel Komut Satırı ile

### Adım 1: Çıktı klasörü oluşturun

```bash
mkdir out
```

### Adım 2: Java dosyalarını derleyin

**Windows (Command Prompt):**
```cmd
javac -d out -encoding UTF-8 src/main/java/com/parkomfy/model/*.java src/main/java/com/parkomfy/service/*.java src/main/java/com/parkomfy/repository/*.java src/main/java/com/parkomfy/*.java
```

**Mac/Linux:**
```bash
find src/main/java -name "*.java" > sources.txt
javac -d out -encoding UTF-8 @sources.txt
rm sources.txt
```

### Adım 3: Uygulamayı çalıştırın

```bash
java -cp out com.parkomfy.ParkomfyApplication
```

---

## Yöntem 3: Maven ile (Eğer Maven yüklüyse)

### Maven kurulumunu kontrol edin:
```bash
mvn -version
```

### Projeyi derleyin ve çalıştırın:

```bash
# Derle
mvn compile

# Çalıştır
mvn exec:java -Dexec.mainClass="com.parkomfy.ParkomfyApplication"
```

VEYA

```bash
# Tek komutla derle ve çalıştır
mvn compile exec:java -Dexec.mainClass="com.parkomfy.ParkomfyApplication"
```

---

## Yöntem 4: IDE ile (IntelliJ IDEA / Eclipse / VS Code)

### IntelliJ IDEA:

1. Projeyi açın: `File` → `Open` → Proje klasörünü seçin
2. `src/main/java/com/parkomfy/ParkomfyApplication.java` dosyasını açın
3. Sağ tıklayın → `Run 'ParkomfyApplication.main()'`

### Eclipse:

1. `File` → `Import` → `Existing Projects into Workspace`
2. Proje klasörünü seçin
3. `ParkomfyApplication.java` dosyasına sağ tıklayın → `Run As` → `Java Application`

### VS Code:

1. Java Extension Pack'i yükleyin
2. `ParkomfyApplication.java` dosyasını açın
3. `Run` butonuna tıklayın veya `F5` tuşuna basın

---

## Beklenen Çıktı

Uygulama başarıyla çalıştığında şuna benzer bir çıktı göreceksiniz:

```
=== PARKOMFY System Demonstration ===

1. Initial Parking Area Status:
ParkingArea{areaId='AREA-001', areaName='Özyeğin University Parking', totalCapacity=20, availableSlots=20, occupiedSlots=0, occupancyRate=0.00%}
Available slots: 20

2. Vehicle Entry - License Plate Recognition:
License Plate Detected: 34ABC123
Confidence: 85.00%
Vehicle created: Vehicle{vehicleId='VEH-...', licensePlate=34ABC123, ...}

3. Finding Available Parking Slot:
Selected slot: Floor 1, Zone A, Slot 1

4. Occupying Parking Slot:
Session created: SESSION-...
Slot status: OCCUPIED

5. Real-time Detection (YOLO):
Detection result: DetectionResult{...}
Detection accuracy: 95.00%

6. Vehicle Exit - Fee Calculation:
Parking duration: 2 minutes
Fee calculated: 10.0 TRY

7. Processing Payment (Stripe):
Payment status: COMPLETED
Transaction ID: txn_...

8. Final Parking Area Status:
ParkingArea{...}
Available slots: 20

=== Demonstration Complete ===
```

---

## Sorun Giderme

### Hata: "javac: command not found"
**Çözüm:** Java JDK yüklü değil veya PATH'e ekli değil. Java'yı yükleyin ve PATH'i güncelleyin.

### Hata: "package com.parkomfy does not exist"
**Çözüm:** Derleme sırasında tüm Java dosyalarını dahil ettiğinizden emin olun. Script'leri kullanmanız önerilir.

### Hata: "Could not find or load main class"
**Çözüm:** `-cp out` parametresinin doğru olduğundan emin olun. `out` klasöründe `.class` dosyalarının olduğunu kontrol edin.

### Hata: "UnsupportedClassVersionError"
**Çözüm:** Java versiyonunuz 11'den düşük. Java 11 veya üzeri yükleyin.

---

## Notlar

- Bu uygulama **simülasyon** modunda çalışır (gerçek YOLO/Stripe entegrasyonu yok)
- Veritabanı bağlantısı simüle edilmiştir (gerçek MySQL gerekmez)
- Tüm işlemler konsola yazdırılır

---

## Hızlı Başlangıç (Özet)

**En kolay yöntem:**

1. Windows: `run.bat` dosyasına çift tıklayın
2. Mac/Linux: Terminal'de `./run.sh` çalıştırın

Bu kadar! 🚀
