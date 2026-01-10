# 🔧 Java Kurulumu - Hızlı Çözüm

## ⚡ En Hızlı Yöntem (5 Dakika)

### Adım 1: Java İndir
1. Bu linke tıklayın: **[Adoptium Temurin 17 İndir](https://adoptium.net/temurin/releases/?version=17)**
2. **"Windows x64"** seçin
3. **"JDK"** seçin (JRE değil!)
4. **"Installer" (.msi)** seçin
5. İndir butonuna tıklayın

### Adım 2: Kur
1. İndirdiğiniz `.msi` dosyasını çalıştırın
2. **"Set JAVA_HOME variable"** ✅ işaretleyin
3. **"Add to PATH"** ✅ işaretleyin
4. "Next" → "Install" → Kurulumu tamamlayın

### Adım 3: Test Et
1. **YENİ bir Command Prompt açın** (eski pencereyi kapatın!)
2. Şu komutu yazın:
   ```cmd
   java -version
   ```
3. Versiyon bilgisi görünüyorsa ✅ **BAŞARILI!**

### Adım 4: Projeyi Çalıştır
```cmd
run.bat
```

---

## 🎯 Alternatif: Oracle JDK

1. **[Oracle JDK 17 İndir](https://www.oracle.com/java/technologies/downloads/#java17)**
2. Windows x64 Installer'ı indir
3. Kurulum sırasında **"Add to PATH"** seçeneğini işaretle
4. Kurulumu tamamla

---

## ✅ Kurulum Kontrolü

Kurulumdan sonra **MUTLAKA yeni bir terminal açın** ve test edin:

```cmd
java -version
javac -version
```

Her ikisi de versiyon göstermeli.

---

## 🆘 Hala Çalışmıyorsa

### Yöntem 1: PATH'i Manuel Ekle

1. Windows tuşu + "Environment Variables" ara
2. "Edit the system environment variables" aç
3. "Environment Variables" butonuna tıkla
4. "System variables" → "Path" seç → "Edit"
5. "New" → Java'nın `bin` klasörünü ekle:
   ```
   C:\Program Files\Eclipse Adoptium\jdk-17.0.10.9-hotspot\bin
   ```
   (Veya Java'nın kurulu olduğu gerçek yol)
6. Tüm pencereleri "OK" ile kapat
7. **YENİ terminal aç** ve test et

### Yöntem 2: run_fixed.bat Kullan

Java yüklü ama PATH'te değilse, `run_fixed.bat` dosyasını kullanın. Bu script Java'yı otomatik bulur.

---

## 📝 Notlar

- ✅ **JDK** yükleyin (JRE değil) - çünkü `javac` gerekli
- ✅ Kurulumdan sonra **mutlaka yeni terminal açın**
- ✅ Eğer hala çalışmıyorsa, bilgisayarı **yeniden başlatın**

---

## 🚀 Hızlı Linkler

- **Adoptium (Önerilen):** https://adoptium.net/temurin/releases/?version=17
- **Oracle JDK:** https://www.oracle.com/java/technologies/downloads/#java17

**Java'yı kurduktan sonra `run.bat` dosyasını tekrar çalıştırın!** 🎉
