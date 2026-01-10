# Java Kurulum Kılavuzu - Windows

## 🚀 Hızlı Kurulum (Önerilen)

### Yöntem 1: Oracle JDK (Resmi)

1. **Java İndir:**
   - [Oracle JDK 17 İndir](https://www.oracle.com/java/technologies/downloads/#java17)
   - Veya [Oracle JDK 21 İndir](https://www.oracle.com/java/technologies/downloads/#java21) (En güncel)

2. **Kurulum:**
   - İndirdiğiniz `.exe` dosyasını çalıştırın
   - "Next" butonlarına tıklayarak ilerleyin
   - **ÖNEMLİ:** "Add to PATH" seçeneğinin işaretli olduğundan emin olun
   - Kurulumu tamamlayın

3. **Kontrol:**
   - Yeni bir Command Prompt açın (eski pencereyi kapatın)
   - Şu komutu çalıştırın:
   ```cmd
   java -version
   ```
   - Şuna benzer bir çıktı görmelisiniz:
   ```
   java version "17.0.x" 2023-xx-xx LTS
   Java(TM) SE Runtime Environment (build 17.0.x+xx-LTS-xxx)
   Java HotSpot(TM) 64-Bit Server VM (build 17.0.x+xx-LTS-xxx, mixed mode, sharing)
   ```

---

### Yöntem 2: OpenJDK (Ücretsiz, Açık Kaynak) - ÖNERİLEN

1. **Adoptium (Eskiden AdoptOpenJDK) - EN KOLAY:**
   - [Adoptium Temurin 17 İndir](https://adoptium.net/temurin/releases/?version=17)
   - "Windows x64" seçin
   - "JDK" seçin (JRE değil)
   - İndir ve kur

2. **Kurulum:**
   - İndirdiğiniz `.msi` dosyasını çalıştırın
   - "Set JAVA_HOME variable" seçeneğini işaretleyin
   - "Add to PATH" seçeneğini işaretleyin
   - Kurulumu tamamlayın

3. **Kontrol:**
   - Yeni bir Command Prompt açın
   ```cmd
   java -version
   ```

---

### Yöntem 3: Chocolatey ile (Eğer Chocolatey yüklüyse)

```powershell
choco install openjdk17
```

VEYA

```powershell
choco install openjdk
```

---

### Yöntem 4: Winget ile (Windows 10/11)

```cmd
winget install Microsoft.OpenJDK.17
```

---

## ✅ Kurulum Sonrası Kontrol

### 1. Java versiyonunu kontrol et:
```cmd
java -version
```

### 2. Java derleyiciyi kontrol et:
```cmd
javac -version
```

### 3. PATH'i kontrol et:
```cmd
echo %PATH%
```

Java yolunun (`C:\Program Files\Java\...`) PATH'te olduğundan emin olun.

---

## 🔧 PATH Sorunu Varsa (Manuel Ekleme)

Eğer Java yüklü ama PATH'te değilse:

### 1. Java'nın kurulu olduğu yeri bul:
Genellikle:
- `C:\Program Files\Java\jdk-17\bin`
- `C:\Program Files\Java\jdk-21\bin`
- `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot\bin`

### 2. PATH'e ekle:

**Windows 10/11:**
1. "Environment Variables" araması yapın
2. "Edit the system environment variables" açın
3. "Environment Variables" butonuna tıklayın
4. "System variables" altında "Path" seçin
5. "Edit" butonuna tıklayın
6. "New" butonuna tıklayın
7. Java'nın `bin` klasörünün yolunu ekleyin (örn: `C:\Program Files\Java\jdk-17\bin`)
8. "OK" ile tüm pencereleri kapatın

**Command Prompt ile (Yönetici olarak):**
```cmd
setx PATH "%PATH%;C:\Program Files\Java\jdk-17\bin" /M
```

⚠️ **ÖNEMLİ:** Yeni bir Command Prompt açın (eski pencereyi kapatın)

---

## 🎯 Hızlı Test

Java kurulumundan sonra:

1. **Yeni bir Command Prompt açın** (eski pencereyi kapatın!)
2. Şu komutu çalıştırın:
   ```cmd
   java -version
   ```
3. Eğer versiyon bilgisi görünüyorsa, başarılı! ✅
4. Şimdi `run.bat` dosyasını çalıştırabilirsiniz

---

## 📝 Notlar

- **JDK** yükleyin (JRE değil) - çünkü `javac` (derleyici) gerekli
- Kurulumdan sonra **mutlaka yeni bir terminal açın**
- Eğer hala çalışmıyorsa, bilgisayarı yeniden başlatın

---

## 🆘 Hala Çalışmıyorsa

1. Java'nın gerçekten yüklü olduğunu kontrol edin:
   ```cmd
   dir "C:\Program Files\Java"
   ```

2. Tam yolu kullanarak test edin:
   ```cmd
   "C:\Program Files\Java\jdk-17\bin\java.exe" -version
   ```

3. Eğer bu çalışıyorsa, PATH sorunu var demektir (yukarıdaki PATH ekleme adımlarını takip edin)

---

## 💡 Önerilen: Adoptium Temurin 17

En kolay ve güvenilir yöntem:
1. [Adoptium Temurin 17 İndir](https://adoptium.net/temurin/releases/?version=17)
2. Windows x64 JDK .msi dosyasını indir
3. Kurulum sırasında "Add to PATH" seçeneğini işaretle
4. Kurulumu tamamla
5. Yeni terminal aç ve test et

**Başarılar! 🚀**
