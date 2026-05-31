# Dağıtık Dijital Kütüphane Arama Motoru

Bu proje, Hadoop MapReduce kullanarak büyük kitap veri kümeleri üzerinde TF-IDF tabanlı bir ters dizin (inverted index) oluşturan ve WebHDFS REST API ile gerçek zamanlı arama sunan Java Swing tabanlı dağıtık bir arama motorudur.

## Çalıştırma Adımları

Projeyi kendi bilgisayarınızda çalıştırmak için sırasıyla aşağıdaki adımları takip edebilirsiniz:

### 1. Docker Hadoop Kümesini Başlatın
Hadoop kümesini (1 NameNode, 2 DataNode, YARN ResourceManager, NodeManager ve HistoryServer) ayağa kaldırmak için terminalde projenin olduğu klasöre gelin ve şu komutu çalıştırın:
```bash
docker-compose up -d
```
*(Docker Desktop uygulamasının açık olduğundan emin olun).*

### 2. Projeyi Derleyin
Java kodlarını derlemek ve çalıştırılabilir JAR dosyasını üretmek için Maven komutunu çalıştırın:
```bash
mvn clean package
```
Bu komut, projenin `target` klasörünün altında `digital-library-search-1.0-SNAPSHOT.jar` adında bir dosya oluşturacaktır.

### 3. Derlenen JAR Dosyasını Hadoop Kümesine Gönderin
MapReduce işlerinin Hadoop kümesi (Docker) içinde çalışabilmesi için üretilen JAR dosyasını NameNode konteynerine kopyalamanız gerekir. Bunun için PowerShell kullanıyorsanız direkt şu betiği çalıştırabilirsiniz:
```powershell
./build.ps1
```
Ya da manuel olarak şu komutu yazabilirsiniz:
```bash
docker cp target/digital-library-search-1.0-SNAPSHOT.jar namenode:/digital-library-search.jar
```

### 4. Arayüzü (GUI) Başlatın
Java Swing görsel arayüzünü başlatmak için şu komutu çalıştırın:
```bash
java -cp target/digital-library-search-1.0-SNAPSHOT.jar com.bdata.LibraryGUI
```
Bu komut sonrası karşınıza **HDFS Manager**, **MapReduce Job Monitor** ve **Ranked Search** sekmelerinden oluşan uygulama ekranı gelecektir.

### 5. Uygulamayı Kullanın
*   **HDFS Manager:** Sisteme kitap veri tabanı yüklemek veya dizinleri incelemek için kullanabilirsiniz. (Hadoop kümesine kitapları `/input/all_books` şeklinde yükleyebilirsiniz).
*   **MapReduce Job Monitor:** Veri seti boyutunu seçip (Small, Half, All) "Run MapReduce Indexing Pipeline" butonuna basarak Hadoop kümesi üzerinde TF-IDF indeksleme zincirini tetikleyebilirsiniz. Konsol logları arayüze canlı akacaktır.
*   **Ranked Search:** İndeksleme bittikten sonra "Load Index" butonuna basıp ardından arama çubuğuna kelime yazarak (örn: `wizard`) kitaplar arasında gerçek zamanlı ve TF-IDF skoruna göre sıralı arama yapabilirsiniz.
