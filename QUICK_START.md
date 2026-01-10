# 🚀 PARKOMFY - Hızlı Başlangıç

## ⚡ En Hızlı Yöntem

### Windows:
1. **`run.bat`** dosyasına çift tıklayın
2. VEYA Command Prompt'ta: `run.bat`

### Mac/Linux:
1. Terminal'de: `chmod +x run.sh`
2. Sonra: `./run.sh`

---

## 📋 Gereksinimler

- ✅ **Java 11 veya üzeri** (Java 17 önerilir)

### Java Kontrolü:
```bash
java -version
```

Eğer Java yoksa: [Java İndir](https://www.oracle.com/java/technologies/downloads/)

---

## 🎯 Adım Adım (Manuel)

### 1️⃣ Çıktı klasörü oluştur:
```bash
mkdir out
```

### 2️⃣ Derle:
**Windows:**
```cmd
javac -d out -encoding UTF-8 src\main\java\com\parkomfy\model\*.java src\main\java\com\parkomfy\service\*.java src\main\java\com\parkomfy\repository\*.java src\main\java\com\parkomfy\*.java
```

**Mac/Linux:**
```bash
find src/main/java -name "*.java" > sources.txt
javac -d out -encoding UTF-8 @sources.txt
rm sources.txt
```

### 3️⃣ Çalıştır:
```bash
java -cp out com.parkomfy.ParkomfyApplication
```

---

## 💡 IDE ile Çalıştırma

### IntelliJ IDEA:
1. Projeyi aç
2. `ParkomfyApplication.java` dosyasını aç
3. Sağ tık → `Run 'ParkomfyApplication.main()'`

### VS Code:
1. Java Extension Pack yükle
2. `ParkomfyApplication.java` aç
3. `Run` butonuna tıkla

---

## ✅ Başarılı Çalıştırma

Uygulama çalıştığında şunu göreceksiniz:

```
=== PARKOMFY System Demonstration ===

1. Initial Parking Area Status:
ParkingArea{...}
Available slots: 20

2. Vehicle Entry - License Plate Recognition:
License Plate Detected: 34ABC123
...

=== Demonstration Complete ===
```

---

## ❗ Sorun mu var?

- **"javac: command not found"** → Java JDK yüklü değil
- **"package does not exist"** → Script kullan (run.bat veya run.sh)
- **"UnsupportedClassVersionError"** → Java 11+ yükle

Detaylı bilgi için: `HOW_TO_RUN.md`

---

**🎉 Hazırsın! `run.bat` (Windows) veya `./run.sh` (Mac/Linux) ile başla!**
