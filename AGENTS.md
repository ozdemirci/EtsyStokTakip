# Agent Talimatlari

- Prod amacli profil icin `application-prod.properties` kullan.
- Commit mesajlari kisa ve Ingilizce olmali, fiil ile baslamalidir.
- Uygulama **schema-based** multi-tenancy mimarisi kullanir. Kodunuz bu yapinin gerektirdigi sekilde tenant bazli olmalidir.
- Tenant secimi HTTP istegindeki `X-TenantId` basligi uzerinden yapilir.
- Java 17 ve Maven kullanilir. Build islemlerinde `mvn` komutlarini tercih edin.
- Kodlama standartlari ve detayli kurallar icin `docs/guidelines.md` dosyasina goz atabilirsiniz.
-mümkün oldukça lombok kullanmaya çalış
-tenants lowercase olarak tanımla ve çalıştır
-uygulama başlatmak için docker compose up --build -d --no-cache komutunu kullan
-uygulamayı durdurmak için docker compose down -v --rmi local komutunu kullan
-logları görmek için docker compose logs komutunu kullan
-yeniden çalıştırmak için docker compose restart stockify-app kullan
-terminal olarak PowerShell'de kullanılacak komut satırı yaz
-cd c:\Users\ozdem\IdeaProjects\STOCKIFY; şeklinde yazmana gerek yok terminal her zaman bu konumda
-# Windows'ta PowerShell versiyonu:
./check-health.ps1
-








